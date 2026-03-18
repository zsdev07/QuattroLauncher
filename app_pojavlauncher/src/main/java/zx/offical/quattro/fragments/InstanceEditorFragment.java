package zx.offical.quattro.fragments;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ProgressBar;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import zx.offical.quattro.R;
import zx.offical.quattro.Tools;
import zx.offical.quattro.extra.ExtraConstants;
import zx.offical.quattro.extra.ExtraCore;
import zx.offical.quattro.instances.Instance;
import zx.offical.quattro.instances.Instances;
import zx.offical.quattro.multirt.MultiRTUtils;
import zx.offical.quattro.mods.InstanceModManager;
import zx.offical.quattro.mods.InstanceModsAdapter;
import zx.offical.quattro.mods.LocalModFile;
import zx.offical.quattro.multirt.RTSpinnerAdapter;
import zx.offical.quattro.multirt.Runtime;
import zx.offical.quattro.instances.InstanceIconProvider;
import zx.offical.quattro.profiles.VersionSelectorDialog;
import zx.offical.quattro.utils.CropperUtils;
import zx.offical.quattro.utils.RendererCompatUtil;

import java.io.IOException;
import java.util.Collections;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import zx.offical.quattro.PojavApplication;

public class InstanceEditorFragment extends Fragment implements CropperUtils.CropperReceiver {
    public static final String TAG = "InstanceEditorFragment";

    private Instance mInstance;
    private String mSelectedControlLayout;
    private Button mSaveButton, mDeleteButton, mControlSelectButton, mVersionSelectButton, mManageModsButton;
    private Spinner mDefaultRuntime, mDefaultRenderer;
    private EditText mDefaultName, mDefaultJvmArgument;
    private TextView mDefaultVersion, mDefaultControl, mManageModsStatus;
    private ImageView mInstanceIcon;
    private CheckBox mSharedDataCheckbox;
    private int mRecommendedIconSize;
    private final ActivityResultLauncher<?> mCropperLauncher = CropperUtils.registerCropper(this, this);

    private List<String> mRenderNames;

    public InstanceEditorFragment(){
        super(R.layout.fragment_instance_editor);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        // Paths, which can be changed
        String value = (String) ExtraCore.consumeValue(ExtraConstants.FILE_SELECTOR);
        if(value != null){
            mSelectedControlLayout = value;
        }
        return super.onCreateView(inflater, container, savedInstanceState);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        bindViews(view);

        RendererCompatUtil.RenderersList renderersList = RendererCompatUtil.getCompatibleRenderers(view.getContext());
        mRenderNames = renderersList.rendererIds;
        List<String> renderList = new ArrayList<>(renderersList.rendererDisplayNames.length + 1);
        renderList.addAll(Arrays.asList(renderersList.rendererDisplayNames));
        renderList.add(view.getContext().getString(R.string.global_default));
        mDefaultRenderer.setAdapter(new ArrayAdapter<>(view.getContext(), R.layout.item_simple_list_1, renderList));

        // Set up behaviors
        mSaveButton.setOnClickListener(v -> {
            InstanceIconProvider.dropIcon(mInstance);
            save();
            Tools.backToMainMenu(requireActivity());
        });

        mDeleteButton.setOnClickListener(v -> {
            InstanceIconProvider.dropIcon(mInstance);
            Tools.removeCurrentFragment(requireActivity());
            try {
                Instances.removeInstance(mInstance);
            }catch (IOException e) {
                Tools.showErrorRemote(e);
            }
        });

        View.OnClickListener controlSelectListener = getControlSelectListener();
        mControlSelectButton.setOnClickListener(controlSelectListener);
        mDefaultControl.setOnClickListener(controlSelectListener);

        // Setup the expendable list behavior
        View.OnClickListener versionSelectListener = getVersionSelectListener();
        mVersionSelectButton.setOnClickListener(versionSelectListener);
        mDefaultVersion.setOnClickListener(versionSelectListener);

        mManageModsButton.setOnClickListener(v -> openModManagerDialog());

        // Set up the icon change click listener
        mInstanceIcon.setOnClickListener(v -> {
            // Fill recommended size on click to ge the most up to date data
            mRecommendedIconSize = Math.max(v.getWidth(), v.getHeight());
            CropperUtils.startCropper(mCropperLauncher);
        });

        mSharedDataCheckbox.setOnCheckedChangeListener((v,checked) ->{
            mInstance.sharedData = checked;
            int text = R.string.instance_shared_data_off;
            if(checked) text = R.string.instance_shared_data_on;
            mSharedDataCheckbox.setText(text);
            refreshModManagerState();
        });

        Instance selectedInstance = Instances.loadSelectedInstance();
        Context context = view.getContext();
        if(selectedInstance == null) {
            Toast.makeText(context, R.string.no_instance, Toast.LENGTH_LONG).show();
            getParentFragmentManager().popBackStack();
        }else {
            loadValues(selectedInstance, context);
        }
    }

    private View.OnClickListener getControlSelectListener() {
        return v -> {
            Bundle bundle = new Bundle(3);
            bundle.putBoolean(FileSelectorFragment.BUNDLE_SELECT_FOLDER, false);
            bundle.putString(FileSelectorFragment.BUNDLE_ROOT_PATH, Tools.CTRLMAP_PATH);

            Tools.swapFragment(requireActivity(),
                    FileSelectorFragment.class, FileSelectorFragment.TAG, bundle);
        };
    }

    private View.OnClickListener getVersionSelectListener() {
        return v -> VersionSelectorDialog.open(v.getContext(), false, (id, snapshot)-> {
            mDefaultVersion.setText(id);
            if (mInstance != null) mInstance.versionId = id;
            refreshModManagerState();
        });
    }

    private static String nullToEmpty(String in) {
        if(in == null) return "";
        return in;
    }

    private void loadValues(@NonNull Instance instance, @NonNull Context context){
        mInstance = instance;
        mInstanceIcon.setImageDrawable(
                InstanceIconProvider.fetchIcon(getResources(), instance)
        );

        // Runtime spinner
        List<Runtime> runtimes = MultiRTUtils.getRuntimes();
        int jvmIndex = -1;
        if(instance.selectedRuntime != null) {
            jvmIndex = runtimes.indexOf(new Runtime(instance.selectedRuntime));
        }
        mDefaultRuntime.setAdapter(new RTSpinnerAdapter(context, runtimes));
        if(jvmIndex == -1) jvmIndex = runtimes.size() - 1;
        mDefaultRuntime.setSelection(jvmIndex);

        // Renderer spinner
        int rendererIndex = mRenderNames.indexOf(instance.getLaunchRenderer());
        if(rendererIndex == -1) {
            rendererIndex = mDefaultRenderer.getAdapter().getCount() - 1;
        }
        mDefaultRenderer.setSelection(rendererIndex);

        mDefaultVersion.setText(instance.versionId);
        mDefaultJvmArgument.setText(nullToEmpty(instance.jvmArgs));
        mDefaultName.setText(nullToEmpty(instance.name));
        mDefaultControl.setText(mSelectedControlLayout == null ? nullToEmpty(instance.controlLayout) : mSelectedControlLayout);
        mSharedDataCheckbox.setChecked(instance.sharedData);
        refreshModManagerState();
    }


    private void refreshModManagerState() {
        if (mInstance == null) return;
        InstanceModManager.ModSupportInfo supportInfo = InstanceModManager.getSupportInfo(mInstance);
        mManageModsButton.setEnabled(supportInfo.isSupported);
        mManageModsButton.setAlpha(supportInfo.isSupported ? 1f : 0.5f);
        mManageModsStatus.setText(supportInfo.isSupported
                ? getString(R.string.instance_mod_manager_ready, supportInfo.modsDirectory.getAbsolutePath())
                : supportInfo.reason);
    }

    private void openModManagerDialog() {
        if (mInstance == null) return;
        InstanceModManager.ModSupportInfo supportInfo = InstanceModManager.getSupportInfo(mInstance);
        if (!supportInfo.isSupported) return;

        Context context = requireContext();
        View dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_instance_mod_manager, null, false);
        TextView summaryView = dialogView.findViewById(R.id.instance_mod_manager_summary);
        ProgressBar progressBar = dialogView.findViewById(R.id.instance_mod_manager_progress);
        TextView emptyView = dialogView.findViewById(R.id.instance_mod_manager_empty);
        RecyclerView recyclerView = dialogView.findViewById(R.id.instance_mod_manager_list);
        recyclerView.setLayoutManager(new LinearLayoutManager(context));

        summaryView.setText(getString(R.string.instance_mod_manager_ready, supportInfo.modsDirectory.getAbsolutePath()));

        InstanceModsAdapter adapter = new InstanceModsAdapter((mod, enabled, position) ->
                PojavApplication.sExecutorService.execute(() -> {
                    try {
                        InstanceModManager.setModEnabled(mod, enabled);
                        Tools.runOnUiThread(() -> {
                            adapter.notifyItemChanged(position);
                            Toast.makeText(context, enabled
                                    ? R.string.instance_mod_toggle_enabled_message
                                    : R.string.instance_mod_toggle_disabled_message, Toast.LENGTH_SHORT).show();
                        });
                    } catch (IOException e) {
                        Tools.runOnUiThread(() -> {
                            mod.enabled = !enabled;
                            adapter.notifyItemChanged(position);
                            Tools.showError(context, e);
                        });
                    }
                }));
        recyclerView.setAdapter(adapter);

        AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle(R.string.instance_mod_manager_title)
                .setView(dialogView)
                .setPositiveButton(android.R.string.ok, null)
                .show();

        PojavApplication.sExecutorService.execute(() -> {
            List<LocalModFile> mods = Collections.emptyList();
            try {
                mods = InstanceModManager.scanMods(mInstance);
            } catch (RuntimeException e) {
                List<LocalModFile> finalMods = mods;
                Tools.runOnUiThread(() -> {
                    if (!dialog.isShowing()) return;
                    progressBar.setVisibility(View.GONE);
                    emptyView.setVisibility(View.VISIBLE);
                    recyclerView.setVisibility(View.GONE);
                    emptyView.setText(R.string.instance_mod_manager_scan_failed);
                    Tools.showError(context, e);
                });
                return;
            }
            List<LocalModFile> finalMods = mods;
            Tools.runOnUiThread(() -> {
                if (!dialog.isShowing()) return;
                progressBar.setVisibility(View.GONE);
                adapter.setItems(finalMods);
                boolean hasMods = !finalMods.isEmpty();
                recyclerView.setVisibility(hasMods ? View.VISIBLE : View.GONE);
                emptyView.setVisibility(hasMods ? View.GONE : View.VISIBLE);
            });
        });
    }

    private void bindViews(@NonNull View view){
        mDefaultControl = view.findViewById(R.id.vprof_editor_ctrl_spinner);
        mDefaultRuntime = view.findViewById(R.id.vprof_editor_spinner_runtime);
        mDefaultRenderer = view.findViewById(R.id.vprof_editor_instance_renderer);
        mDefaultVersion = view.findViewById(R.id.vprof_editor_version_spinner);

        mDefaultName = view.findViewById(R.id.vprof_editor_instance_name);
        mDefaultJvmArgument = view.findViewById(R.id.vprof_editor_jre_args);

        mSaveButton = view.findViewById(R.id.vprof_editor_save_button);
        mDeleteButton = view.findViewById(R.id.vprof_editor_delete_button);
        mManageModsButton = view.findViewById(R.id.vprof_editor_manage_mods_button);
        mControlSelectButton = view.findViewById(R.id.vprof_editor_ctrl_button);
        mVersionSelectButton = view.findViewById(R.id.vprof_editor_version_button);
        mManageModsStatus = view.findViewById(R.id.vprof_editor_manage_mods_status);
        mInstanceIcon = view.findViewById(R.id.vprof_editor_instance_icon);
        mSharedDataCheckbox = view.findViewById(R.id.vprof_editor_data_checkbox_container);
    }

    private void save(){
        //First, check for potential issues in the inputs
        mInstance.versionId = mDefaultVersion.getText().toString();
        mInstance.controlLayout = mDefaultControl.getText().toString();
        mInstance.name = mDefaultName.getText().toString();
        mInstance.jvmArgs = mDefaultJvmArgument.getText().toString();

        if(mInstance.controlLayout.isEmpty()) mInstance.controlLayout = null;
        if(mInstance.jvmArgs.isEmpty()) mInstance.jvmArgs = null;

        Runtime selectedRuntime = (Runtime) mDefaultRuntime.getSelectedItem();
        mInstance.selectedRuntime = (selectedRuntime.name.equals("<Default>") || selectedRuntime.versionString == null)
                ? null : selectedRuntime.name;

        if(mDefaultRenderer.getSelectedItemPosition() == mRenderNames.size()) mInstance.renderer = null;
        else mInstance.renderer = mRenderNames.get(mDefaultRenderer.getSelectedItemPosition());

        try {
            mInstance.write();
        }catch (IOException e) {
            Tools.showErrorRemote(e);
        }
    }

    @Override
    public float getAspectRatio() {
        return 1f;
    }

    @Override
    public int getTargetMaxSide() {
        return mRecommendedIconSize;
    }

    @Override
    public void onCropped(Bitmap contentBitmap) {
        mInstanceIcon.setImageBitmap(contentBitmap);
        Log.i("bitmap", "w="+contentBitmap.getWidth() +" h="+contentBitmap.getHeight());
        try {
            mInstance.encodeNewIcon(contentBitmap);
        }catch (IOException e) {
            Tools.showErrorRemote(e);
        }
    }

    @Override
    public void onFailed(Exception exception) {
        Tools.showErrorRemote(exception);
    }
}
