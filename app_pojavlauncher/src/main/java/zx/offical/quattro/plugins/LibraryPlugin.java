package zx.offical.quattro.plugins;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.util.Log;

import java.io.File;
import zx.offical.quattro.BuildConfig;

public class LibraryPlugin {
    private static final String TAG = "LibraryPlugin";

    // Known plugins constants
    public static final String ID_ANGLE_PLUGIN = BuildConfig.PLUGIN_PACKAGE_ANGLE;
    public static final String ID_FFMPEG_PLUGIN = BuildConfig.PLUGIN_PACKAGE_FFMPEG;

    private String appId;
    private String libraryPath;
    private LibraryPlugin(String app, String libraryPath){
        this.appId = app;
        this.libraryPath = libraryPath;
    }
    public static LibraryPlugin discoverPlugin(Context ctx, String appId){

        String libraryPath;
        try {
            PackageInfo pluginPackage = ctx.getPackageManager().getPackageInfo(appId, PackageManager.GET_SHARED_LIBRARY_FILES);
            libraryPath = pluginPackage.applicationInfo.nativeLibraryDir;

        } catch (PackageManager.NameNotFoundException e){
            Log.i(TAG, "Plugin not visible/installed: " + appId);
            return null;
        } catch (Exception e){
            Log.e(TAG, "Plugin discover failed for " + appId, e);
            return null;
        }
       return new LibraryPlugin(appId, libraryPath);
    }

    public String getId(){
        return appId;
    }

    public String getLibraryPath(){
        return libraryPath;
    }
    public String resolveAbsolutePath(String library) {
        return new File(libraryPath, library).getAbsolutePath();
    }

    public boolean checkLibraries(String... libs){
        for(String lib : libs){
            if(!(new File(libraryPath, lib).exists())) return false;
        }
        return true;
    }
}
