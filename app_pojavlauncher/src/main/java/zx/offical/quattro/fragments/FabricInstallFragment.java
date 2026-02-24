package zx.offical.quattro.fragments;

import zx.offical.quattro.modloaders.FabriclikeUtils;
import zx.offical.quattro.modloaders.ModloaderListenerProxy;

public class FabricInstallFragment extends FabriclikeInstallFragment {

    public static final String TAG = "FabricInstallFragment";

    public FabricInstallFragment() {
        super(FabriclikeUtils.FABRIC_UTILS, TAG);
    }
}
