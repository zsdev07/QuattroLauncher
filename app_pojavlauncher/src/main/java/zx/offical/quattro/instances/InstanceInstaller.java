package zx.offical.quattro.instances;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.AssetManager;
import android.os.Bundle;
import android.util.Log;

import com.kdt.mcgui.ProgressLayout;

import zx.offical.quattro.JavaGUILauncherActivity;
import zx.offical.quattro.LauncherActivity;
import zx.offical.quattro.PojavApplication;
import zx.offical.quattro.Tools;
import zx.offical.quattro.extra.ExtraConstants;
import zx.offical.quattro.extra.ExtraCore;
import zx.offical.quattro.instances.profcompat.ProfileWatcher;
import zx.offical.quattro.lifecycle.ContextExecutor;
import zx.offical.quattro.lifecycle.ContextExecutorTask;
import zx.offical.quattro.modloaders.OFDownloadPageScraper;
import zx.offical.quattro.progresskeeper.DownloaderProgressWrapper;
import zx.offical.quattro.utils.DownloadUtils;
import zx.offical.quattro.utils.JSONUtils;
import zx.offical.quattro.utils.NotificationUtils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import zx.offical.quattro.R;

public class InstanceInstaller implements ContextExecutorTask {
    private static final String TAG = "InstanceInstaller";
    private static final File sLastInstallInfo = new File(Tools.DIR_CACHE, "last_installer.json");

    private enum ErrorCategory {
        URL_TRANSFORM,
        DOWNLOAD,
        STATE_WRITE,
        STATE_READ,
        STATE_CLEANUP,
        PROFILE_UPDATE
    }

    private static final class InstallerException extends IOException {
        public final ErrorCategory category;

        InstallerException(ErrorCategory category, String message, Throwable cause) {
            super(message, cause);
            this.category = category;
        }

        InstallerException(ErrorCategory category, String message) {
            super(message);
            this.category = category;
        }
    }

    public String installerJar;
    private transient File installerJarFile;
    private transient String mTransformedUrl;
    public List<String> commandLineArgs;
    public String installerUrlTransformer;
    public String installerDownloadUrl;
    public String installerSha1;

    private File installerJar() {
        if (installerJarFile == null) return installerJarFile = new File(installerJar);
        return installerJarFile;
    }

    private String installerDownloadUrl() throws IOException {
        if (mTransformedUrl != null) return mTransformedUrl;
        String newUrl;
        try {
            if ("optifine".equals(installerUrlTransformer)) {
                newUrl = OFDownloadPageScraper.run(installerDownloadUrl);
            } else {
                newUrl = installerDownloadUrl;
            }
        } catch (IOException e) {
            throw new InstallerException(ErrorCategory.URL_TRANSFORM, "Failed to resolve installer URL", e);
        } catch (RuntimeException e) {
            throw new InstallerException(ErrorCategory.URL_TRANSFORM, "Unexpected failure while resolving installer URL", e);
        }
        mTransformedUrl = newUrl;
        return newUrl;
    }

    private void writeLastInstallerAtomic() throws IOException {
        File parent = sLastInstallInfo.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new InstallerException(ErrorCategory.STATE_WRITE, "Failed to create installer state directory");
        }

        File tempFile = new File(sLastInstallInfo.getAbsolutePath() + ".tmp");
        JSONUtils.writeToFile(tempFile, this);

        try {
            if (sLastInstallInfo.exists() && !sLastInstallInfo.delete()) {
                throw new InstallerException(ErrorCategory.STATE_WRITE, "Failed to replace previous installer state file");
            }
            if (!tempFile.renameTo(sLastInstallInfo)) {
                throw new InstallerException(ErrorCategory.STATE_WRITE, "Failed to atomically publish installer state file");
            }
            logTransition("STATE_WRITTEN");
        } finally {
            if (tempFile.exists() && !tempFile.delete()) {
                Log.w(TAG, "Failed to delete temporary installer state file: " + tempFile.getAbsolutePath());
            }
        }
    }

    private static void logTransition(String transition) {
        Log.i(TAG, "Lifecycle transition: " + transition);
    }

    private static void deleteFileIfExists(File file, String purpose) throws IOException {
        if (file.exists() && !file.delete()) {
            throw new InstallerException(ErrorCategory.STATE_CLEANUP, "Failed to delete " + purpose + ": " + file.getAbsolutePath());
        }
    }

    private static void clearInstallerStateFilesQuietly() {
        if (sLastInstallInfo.isFile() && !sLastInstallInfo.delete()) {
            Log.w(TAG, "Failed to delete installer state file during recovery: " + sLastInstallInfo.getAbsolutePath());
        }
        File tempFile = new File(sLastInstallInfo.getAbsolutePath() + ".tmp");
        if (tempFile.isFile() && !tempFile.delete()) {
            Log.w(TAG, "Failed to delete installer temp state file during recovery: " + tempFile.getAbsolutePath());
        }
    }

    public void threadedStart() throws IOException {
        try {
            logTransition("DOWNLOAD_STARTED");
            final byte[] buffer = new byte[8192];
            final DownloaderProgressWrapper wrapper = new DownloaderProgressWrapper(
                    R.string.mcl_launch_downloading_progress, ProgressLayout.INSTANCE_INSTALL
            );
            wrapper.extraString = installerJar().getName();
            DownloadUtils.ensureSha1(installerJar(), installerSha1, () -> {
                DownloadUtils.downloadFileMonitored(installerDownloadUrl(), installerJar(), buffer, wrapper);
                return null;
            });
            logTransition("DOWNLOAD_COMPLETED");
            ContextExecutor.execute(this);
            logTransition("GUI_INSTALLER_DISPATCHED");
        } catch (IOException e) {
            throw new InstallerException(ErrorCategory.DOWNLOAD, "Installer download failed", e);
        } catch (RuntimeException e) {
            throw new InstallerException(ErrorCategory.DOWNLOAD, "Unexpected installer download failure", e);
        } finally {
            ProgressLayout.clearProgress(ProgressLayout.INSTANCE_INSTALL);
        }
    }

    private static InstanceInstaller readLastInstallerState() throws IOException {
        try {
            InstanceInstaller state = JSONUtils.readFromFile(sLastInstallInfo, InstanceInstaller.class);
            if (state == null || state.installerJar == null) {
                throw new InstallerException(ErrorCategory.STATE_READ, "Installer state is empty or invalid");
            }
            return state;
        } catch (IOException e) {
            throw new InstallerException(ErrorCategory.STATE_READ, "Failed to read installer state", e);
        } catch (RuntimeException e) {
            throw new InstallerException(ErrorCategory.STATE_READ, "Corrupted installer state JSON", e);
        }
    }

    public static void postInstallCheck(AssetManager assetManager) throws IOException {
        if (!sLastInstallInfo.exists() || !sLastInstallInfo.isFile()) return;

        logTransition("POST_INSTALL_CHECK_STARTED");
        InstanceInstaller lastInstaller = readLastInstallerState();
        try {
            String targetVersionId = ProfileWatcher.consumePendingVersion(assetManager);
            if (targetVersionId == null) {
                logTransition("POST_INSTALL_NO_PENDING_VERSION");
                return;
            }

            for (Instance instance : Instances.loadAllInstances()) {
                if (!lastInstaller.equals(instance.installer)) continue;
                instance.installer = null;
                instance.versionId = targetVersionId;
                instance.write();
            }
            ExtraCore.setValue(ExtraConstants.REFRESH_VERSION_SPINNER, null);
            logTransition("POST_INSTALL_INSTANCE_UPDATED");
        } catch (IOException e) {
            throw new InstallerException(ErrorCategory.PROFILE_UPDATE, "Post-install profile update failed", e);
        } catch (RuntimeException e) {
            throw new InstallerException(ErrorCategory.PROFILE_UPDATE, "Unexpected post-install profile update failure", e);
        } finally {
            deleteFileIfExists(lastInstaller.installerJar(), "installer jar");
            deleteFileIfExists(sLastInstallInfo, "installer state");
            deleteFileIfExists(new File(sLastInstallInfo.getAbsolutePath() + ".tmp"), "installer temp state");
            logTransition("POST_INSTALL_STATE_CLEANED");
        }
    }

    public static void postInstallCheck(Context context) {
        try {
            InstanceInstaller.postInstallCheck(context.getAssets());
        } catch (InstallerException e) {
            Log.e(TAG, "Post-install check failed [" + e.category + "]", e);
            clearInstallerStateFilesQuietly();
            Tools.showError(context, e);
        } catch (IOException e) {
            Log.e(TAG, "Post-install check failed [IO]", e);
            clearInstallerStateFilesQuietly();
            Tools.showError(context, e);
        }
    }

    public void start() {
        ProgressLayout.setProgress(ProgressLayout.INSTANCE_INSTALL, 0);
        PojavApplication.sExecutorService.execute(() -> {
            try {
                threadedStart();
            } catch (InstallerException e) {
                Log.e(TAG, "Installer start failed [" + e.category + "]", e);
                Tools.showErrorRemote(e);
            } catch (IOException e) {
                Log.e(TAG, "Installer start failed [IO]", e);
                Tools.showErrorRemote(e);
            }
        });
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) return true;
        if (!(object instanceof InstanceInstaller)) return false;
        InstanceInstaller that = (InstanceInstaller) object;
        return Objects.equals(installerJar, that.installerJar) &&
                Objects.equals(commandLineArgs, that.commandLineArgs) &&
                Objects.equals(installerDownloadUrl, that.installerDownloadUrl) &&
                Objects.equals(installerUrlTransformer, that.installerUrlTransformer) &&
                Objects.equals(installerSha1, that.installerSha1);
    }

    @Override
    public int hashCode() {
        return Objects.hash(installerJar, commandLineArgs, installerDownloadUrl, installerUrlTransformer, installerSha1);
    }

    @Override
    public void executeWithActivity(Activity activity) {
        try {
            ProfileWatcher.installDefaultProfiles(activity.getAssets());
            logTransition("PREPARE_INSTALLER_STATE");
            writeLastInstallerAtomic();
        } catch (InstallerException e) {
            Log.e(TAG, "Failed before launching GUI installer [" + e.category + "]", e);
            clearInstallerStateFilesQuietly();
            Tools.showError(activity, e);
            return;
        } catch (IOException e) {
            Log.e(TAG, "Failed before launching GUI installer [IO]", e);
            clearInstallerStateFilesQuietly();
            Tools.showError(activity, e);
            return;
        } catch (RuntimeException e) {
            InstallerException wrapped = new InstallerException(ErrorCategory.PROFILE_UPDATE, "Profile preparation failed", e);
            Log.e(TAG, "Failed before launching GUI installer [" + wrapped.category + "]", wrapped);
            clearInstallerStateFilesQuietly();
            Tools.showError(activity, wrapped);
            return;
        }
        Intent intent = new Intent(activity, JavaGUILauncherActivity.class);
        Bundle extras = new Bundle();
        extras.putStringArrayList("javaArgs", new ArrayList<>(commandLineArgs));
        extras.putString("modPath", installerJar);
        intent.putExtras(extras);
        activity.startActivity(intent);
        logTransition("GUI_INSTALLER_STARTED");
    }

    @Override
    public void executeWithApplication(Context context) {
        Tools.runOnUiThread(() -> NotificationUtils.sendBasicNotification(context,
                R.string.modpack_install_notification_title,
                R.string.modpack_install_notification_success,
                new Intent(context, LauncherActivity.class),
                NotificationUtils.PENDINGINTENT_CODE_DOWNLOAD_SERVICE,
                NotificationUtils.NOTIFICATION_ID_DOWNLOAD_LISTENER
        ));
    }
}
