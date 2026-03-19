package zx.offical.quattro.mods;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.SwitchCompat;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import zx.offical.quattro.R;

public class InstanceModsAdapter extends RecyclerView.Adapter<InstanceModsAdapter.ModViewHolder> {
    public interface ToggleListener {
        void onToggleRequested(LocalModFile mod, boolean enabled, int position);
    }

    private final ArrayList<LocalModFile> mMods = new ArrayList<>();
    private final ToggleListener mToggleListener;

    public InstanceModsAdapter(ToggleListener toggleListener) {
        mToggleListener = toggleListener;
    }

    public void setItems(List<LocalModFile> mods) {
        mMods.clear();
        mMods.addAll(mods);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ModViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_instance_mod, parent, false);
        return new ModViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ModViewHolder holder, int position) {
        holder.bind(mMods.get(position), position);
    }

    @Override
    public int getItemCount() {
        return mMods.size();
    }

    class ModViewHolder extends RecyclerView.ViewHolder {
        private final ImageView mIcon;
        private final TextView mTitle;
        private final TextView mSummary;
        private final SwitchCompat mToggle;

        ModViewHolder(@NonNull View itemView) {
            super(itemView);
            mIcon = itemView.findViewById(R.id.instance_mod_icon);
            mTitle = itemView.findViewById(R.id.instance_mod_name);
            mSummary = itemView.findViewById(R.id.instance_mod_summary);
            mToggle = itemView.findViewById(R.id.instance_mod_toggle);
        }

        void bind(LocalModFile mod, int position) {
            mIcon.setImageBitmap(mod.icon);
            if (mod.icon == null) mIcon.setImageResource(R.drawable.ic_px_book);
            mTitle.setText(mod.displayName);
            mSummary.setText(mod.description);
            mToggle.setOnCheckedChangeListener(null);
            mToggle.setEnabled(true);
            mToggle.setChecked(mod.enabled);
            mToggle.setText(mod.enabled ? R.string.instance_mod_enabled : R.string.instance_mod_disabled);
            mToggle.setOnCheckedChangeListener((buttonView, isChecked) -> {
                mToggle.setEnabled(false);
                mToggleListener.onToggleRequested(mod, isChecked, position);
            });
        }
    }
}
