package zx.offical.quattro.fragments;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.kdt.mcgui.ProgressLayout;

import zx.offical.quattro.R;
import zx.offical.quattro.Tools;
import zx.offical.quattro.progresskeeper.ProgressKeeper;

public class SelectAuthFragment extends Fragment {
    public static final String TAG = "AUTH_SELECT_FRAGMENT";

    public SelectAuthFragment(){
        super(R.layout.fragment_select_auth_method);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        Button mMicrosoftButton = view.findViewById(R.id.button_microsoft_authentication);
        Button mLocalButton = view.findViewById(R.id.button_local_authentication);
        Button mElyByButton = view.findViewById(R.id.button_elyby_authentication);

        mMicrosoftButton.setOnClickListener(v -> launchAuthFragment(MicrosoftLoginFragment.class, MicrosoftLoginFragment.TAG));
        mLocalButton.setOnClickListener(v -> launchAuthFragment(LocalLoginFragment.class, LocalLoginFragment.TAG));
        mElyByButton.setOnClickListener(v -> launchAuthFragment(ElyByLoginFragment.class, ElyByLoginFragment.TAG));
    }

    private void launchAuthFragment(Class<? extends  Fragment> fragmentClass, String fragmentTag) {
        if(ProgressKeeper.hasProgressKey(ProgressLayout.AUTHENTICATE)) {
            Toast.makeText(requireContext(), R.string.tasks_ongoing, Toast.LENGTH_SHORT).show();
            return;
        }
        Tools.swapFragment(requireActivity(), fragmentClass, fragmentTag, null);
    }
}
