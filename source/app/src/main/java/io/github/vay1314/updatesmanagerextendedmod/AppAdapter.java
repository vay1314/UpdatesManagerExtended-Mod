package io.github.vay1314.updatesmanagerextendedmod;

import android.view.LayoutInflater;
import android.content.res.ColorStateList;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.color.MaterialColors;

import java.util.List;
import java.util.Set;

public class AppAdapter extends RecyclerView.Adapter<AppAdapter.AppViewHolder> {

    private List<AppInfo> appList;
    private Set<String> toggledApps;
    private OnToggleListener toggleListener;

    public interface OnToggleListener {
        void onToggle(String packageName, boolean isChecked);
    }

    public AppAdapter(List<AppInfo> appList, Set<String> toggledApps, OnToggleListener toggleListener) {
        this.appList = appList;
        this.toggledApps = toggledApps;
        this.toggleListener = toggleListener;
    }

    @NonNull
    @Override
    public AppViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_app, parent, false);
        return new AppViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull AppViewHolder holder, int position) {
        AppInfo app = appList.get(position);
        holder.appName.setText(app.getAppName());
        holder.packageName.setText(app.getPackageName());
        holder.appIcon.setImageDrawable(app.getAppIcon());
        holder.profiles.removeAllViews();
        holder.profiles.setVisibility(app.getProfileBadges().isEmpty() ? View.GONE : View.VISIBLE);
        for (AppInfo.ProfileBadge badge : app.getProfileBadges()) {
            Chip chip = new Chip(holder.itemView.getContext());
            float density = chip.getResources().getDisplayMetrics().density;
            chip.setEnsureMinTouchTargetSize(false);
            chip.setMinHeight(0);
            chip.setMinWidth(0);
            chip.setChipMinHeight(22 * density);
            chip.setTextSize(10);
            chip.setText(badge.label);
            chip.setContentDescription(badge.description);
            chip.setTooltipText(badge.description);
            chip.setMaxWidth((int) (180 * density));
            chip.setEllipsize(TextUtils.TruncateAt.END);
            chip.setChipIconResource(badge.icon);
            chip.setChipIconVisible(true);
            chip.setChipIconSize(13 * density);
            chip.setChipStartPadding(6 * density);
            chip.setChipEndPadding(6 * density);
            chip.setIconStartPadding(0);
            chip.setIconEndPadding(3 * density);
            chip.setTextStartPadding(0);
            chip.setTextEndPadding(0);
            chip.setChipStrokeWidth(0);
            int foreground = MaterialColors.getColor(chip,
                    com.google.android.material.R.attr.colorOnSurfaceVariant);
            chip.setChipBackgroundColor(ColorStateList.valueOf(MaterialColors.getColor(chip,
                    com.google.android.material.R.attr.colorSurfaceVariant)));
            chip.setTextColor(foreground);
            chip.setChipIconTint(ColorStateList.valueOf(foreground));
            chip.setCheckable(false);
            chip.setClickable(false);
            chip.setFocusable(false);
            holder.profiles.addView(chip);
        }

        holder.toggleSwitch.setOnCheckedChangeListener(null);
        holder.toggleSwitch.setChecked(toggledApps.contains(app.getPackageName()));
        holder.toggleSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            toggleListener.onToggle(app.getPackageName(), isChecked);
        });
    }

    @Override
    public int getItemCount() {
        return appList.size();
    }

    static class AppViewHolder extends RecyclerView.ViewHolder {
        ImageView appIcon;
        TextView appName;
        TextView packageName;
        ChipGroup profiles;
        MaterialSwitch toggleSwitch;

        public AppViewHolder(@NonNull View itemView) {
            super(itemView);
            appIcon = itemView.findViewById(R.id.imageViewAppIcon);
            appName = itemView.findViewById(R.id.textViewAppName);
            packageName = itemView.findViewById(R.id.textViewPackageName);
            profiles = itemView.findViewById(R.id.profileBadges);
            toggleSwitch = itemView.findViewById(R.id.toggleSwitch);
        }
    }
}
