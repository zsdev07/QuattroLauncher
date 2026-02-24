package zx.offical.quattro.fragments;

import zx.offical.quattro.modloaders.FabriclikeUtils;
import zx.offical.quattro.modloaders.ModloaderListenerProxy;

public class QuiltInstallFragment extends FabriclikeInstallFragment {

    public static final String TAG = "QuiltInstallFragment";
    private static ModloaderListenerProxy sTaskProxy;

    public QuiltInstallFragment() {
        super(FabriclikeUtils.QUILT_UTILS, TAG);
    }
}
