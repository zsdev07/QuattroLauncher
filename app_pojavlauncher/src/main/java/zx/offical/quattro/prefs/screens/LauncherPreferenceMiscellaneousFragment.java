package zx.offical.quattro.prefs.screens;

import android.content.pm.PackageManager;
import android.os.Bundle;

import androidx.preference.Preference;

import zx.offical.quattro.R;

import zx.offical.quattro.utils.GLInfoUtils;
import zx.offical.quattro.utils.RendererCompatUtil;

public class LauncherPreferenceMiscellaneousFragment extends LauncherPreferenceFragment {
    @Override
    public void onCreatePreferences(Bundle b, String str) {
        addPreferencesFromResource(R.xml.pref_misc);
        Preference driverPreference = requirePreference("zinkPreferSystemDriver");
        PackageManager packageManager = driverPreference.getContext().getPackageManager();
        boolean supportsTurnip = RendererCompatUtil.checkVulkanSupport(packageManager) && GLInfoUtils.getGlInfo().isAdreno();
        driverPreference.setVisible(supportsTurnip);
    }
}
