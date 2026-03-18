package com.example.hydroguard;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;

import java.util.ArrayList;
import java.util.Locale;

/**
 * SensorsAdapter (Rule-aligned)
 * - UI shows Shower/Tap only
 * - L1/L2 hidden from UI
 * - Button states:
 *   leak -> disabled LEAK DETECTED
 *   latched -> RESET
 *   OPEN -> CLOSE
 *   CLOSED/unknown -> OPEN
 */
public class SensorsAdapter extends RecyclerView.Adapter<SensorsAdapter.SensorViewHolder> {

    public interface OnValveActionListener {
        /** command is "OPEN"|"CLOSE"|"RESET" */
        void onValveAction(@NonNull SensorModel sensor, @NonNull String command);
    }

    private final ArrayList<SensorModel> list;
    private final OnValveActionListener listener;

    public SensorsAdapter(ArrayList<SensorModel> list, OnValveActionListener listener) {
        this.list = (list != null) ? list : new ArrayList<>();
        this.listener = listener;
        setHasStableIds(true);
    }

    @Override
    public long getItemId(int position) {
        SensorModel m = (position >= 0 && position < list.size()) ? list.get(position) : null;
        // Stable ID based on line label (L1/L2) to avoid row jumping
        String key = (m != null && m.getSafeLineLabel() != null && !m.getSafeLineLabel().isEmpty())
                ? m.getSafeLineLabel()
                : ("row_" + position);

        return stableId64(key);
    }

    private long stableId64(@NonNull String s) {
        long h = 0xcbf29ce484222325L; // FNV-1a 64
        for (int i = 0; i < s.length(); i++) {
            h ^= s.charAt(i);
            h *= 0x100000001b3L;
        }
        return h;
    }

    @NonNull
    @Override
    public SensorViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_sensor, parent, false);
        return new SensorViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull SensorViewHolder holder, int position) {
        if (position < 0 || position >= list.size()) return;

        SensorModel m = list.get(position);
        if (m == null) return;

        // Always enforce mapping (prevents accidental swapped labels)
        m.applyLineMapping(m.getSafeLineLabel());

        // UI: show Shower/Tap only
        holder.sensorNameText.setText(m.getDisplayName());
        holder.sensorLocationText.setText(safeText(m.location, "-"));

        // Hide internal line label view if present
        if (holder.sensorLineText != null) holder.sensorLineText.setVisibility(View.GONE);

        bindCategoryChip(holder, m);

        float flow = m.getFlow();
        float today = m.getTodayUsage();
        float total = m.getTotal();

        holder.sensorFlowText.setText(String.format(Locale.getDefault(), "Flow: %.2f L/min", flow));
        holder.sensorUsageText.setText(String.format(Locale.getDefault(),
                "Today: %.2f L  •  Total: %.2f L", today, total));

        // Status: derived from leak/latched/mode (more reliable than random text)
        String statusLabel = normalizeStatusLabel(m);
        holder.sensorStatusText.setText(statusLabel.toUpperCase(Locale.getDefault()));
        applyStatusPillStyle(holder.sensorStatusText, statusLabel);

        bindValveButton(holder, m);
    }

    @Override
    public int getItemCount() {
        return list != null ? list.size() : 0;
    }

    // =========================
    // Category chip
    // =========================
    private void bindCategoryChip(@NonNull SensorViewHolder holder, @NonNull SensorModel m) {
        if (holder.sensorCategoryChip == null) return;

        holder.sensorCategoryChip.setText(m.getDisplayName());

        int iconRes = pickCategoryIconSafe(holder, m.getDisplayName());
        if (iconRes != 0) {
            holder.sensorCategoryChip.setChipIconResource(iconRes);
            holder.sensorCategoryChip.setChipIconVisible(true);
        } else {
            holder.sensorCategoryChip.setChipIconVisible(false);
        }
    }

    private int pickCategoryIconSafe(@NonNull SensorViewHolder holder, @NonNull String label) {
        String lower = (label != null ? label : "").toLowerCase(Locale.ROOT);

        if (lower.contains("tap")) {
            return drawableExists(holder, R.drawable.ic_tap) ? R.drawable.ic_tap : 0;
        }
        if (lower.contains("shower")) {
            return drawableExists(holder, R.drawable.ic_shower) ? R.drawable.ic_shower : 0;
        }
        return drawableExists(holder, R.drawable.ic_device) ? R.drawable.ic_device : 0;
    }

    private boolean drawableExists(@NonNull SensorViewHolder holder, @DrawableRes int resId) {
        try {
            return resId != 0 && holder.itemView.getContext().getResources().getResourceName(resId) != null;
        } catch (Exception e) {
            return false;
        }
    }

    // =========================
    // Valve button rules (exact)
    // =========================
    private void bindValveButton(@NonNull SensorViewHolder holder, @NonNull SensorModel m) {
        if (holder.btnValveAction == null) return;

        SensorModel.ValveUi ui = m.computeValveUi();
        applyValveButtonStyle(holder, holder.btnValveAction, ui);

        holder.btnValveAction.setOnClickListener(v -> {
            int pos = holder.getAdapterPosition();
            if (pos == RecyclerView.NO_POSITION) return;
            if (pos < 0 || pos >= list.size()) return;

            SensorModel latest = list.get(pos);
            if (latest == null) return;

            latest.applyLineMapping(latest.getSafeLineLabel());

            SensorModel.ValveUi uiNow = latest.computeValveUi();
            if (!uiNow.enabled) return;
            if ("NONE".equals(uiNow.command)) return;

            if (listener != null) listener.onValveAction(latest, uiNow.command);
        });
    }

    /**
     * Material-ish tonal:
     * - SUCCESS: green-ish
     * - DANGER: red-ish
     * - NEUTRAL: amber-ish
     * - DISABLED: gray-ish
     *
     * (If you later define real Material 3 styles, you can replace these tints easily.)
     */
    private void applyValveButtonStyle(@NonNull SensorViewHolder holder,
                                       @NonNull MaterialButton btn,
                                       @NonNull SensorModel.ValveUi ui) {

        btn.setText(ui.text);
        btn.setEnabled(ui.enabled);
        btn.setAlpha(ui.enabled ? 1f : 0.55f);

        // Optional icons (safe)
        @DrawableRes int iconRes = iconForCommand(ui.command);
        if (drawableExists(holder, iconRes)) btn.setIconResource(iconRes);
        else btn.setIcon(null);

        btn.setIconGravity(MaterialButton.ICON_GRAVITY_TEXT_START);
        btn.setIconPadding(dp(btn, 10));
        btn.setInsetTop(0);
        btn.setInsetBottom(0);

        int bg;
        int fg;
        int stroke;

        switch (ui.tone) {
            case SUCCESS:
                bg = Color.parseColor("#E6F4EA");
                fg = Color.parseColor("#1E8E3E");
                stroke = Color.parseColor("#B7E1C1");
                break;
            case DANGER:
                bg = Color.parseColor("#FCE8E6");
                fg = Color.parseColor("#D93025");
                stroke = Color.parseColor("#F6B8B3");
                break;
            case NEUTRAL:
                bg = Color.parseColor("#FFF4E5");
                fg = Color.parseColor("#F57C00");
                stroke = Color.parseColor("#FFD199");
                break;
            default:
                // disabled
                bg = Color.parseColor("#F2F2F2");
                fg = Color.parseColor("#8A8A8A");
                stroke = Color.parseColor("#DDDDDD");
                break;
        }

        btn.setBackgroundTintList(ColorStateList.valueOf(bg));
        btn.setTextColor(fg);
        btn.setIconTint(ColorStateList.valueOf(fg));

        btn.setStrokeWidth(dp(btn, 1));
        btn.setStrokeColor(ColorStateList.valueOf(stroke));
    }

    @DrawableRes
    private int iconForCommand(@NonNull String command) {
        if ("OPEN".equals(command)) return R.drawable.ic_unlock;
        if ("CLOSE".equals(command)) return R.drawable.ic_lock;
        if ("RESET".equals(command)) return R.drawable.ic_refresh;
        return R.drawable.ic_warning;
    }

    private int dp(@NonNull View v, int dp) {
        float d = v.getResources().getDisplayMetrics().density;
        return Math.round(dp * d);
    }

    // =========================
    // Status mapping (mode + leak + latched)
    // =========================
    private String normalizeStatusLabel(@NonNull SensorModel m) {
        if (m.leak) return "Leak";
        if (m.latched) return "Shutoff";

        String mode = m.getModeSafe();
        if ("SHUTOFF".equals(mode)) return "Shutoff";
        if ("WARNING".equals(mode)) return "Warning";

        // If flow > 0 then running, else idle
        if (m.getFlow() > 0.01f) return "Running";
        return "Normal";
    }

    private void applyStatusPillStyle(TextView pill, String statusLabel) {
        if (pill == null) return;

        pill.setBackgroundResource(R.drawable.bg_status_pill_normal);
        pill.setTextColor(ContextCompat.getColor(pill.getContext(), R.color.hg_onSurface));

        if (equalsAny(statusLabel, "Leak", "Shutoff")) {
            pill.setBackgroundResource(R.drawable.bg_status_pill_shutoff);
            pill.setTextColor(0xFFD32F2F);
            return;
        }

        if (equalsAny(statusLabel, "Warning")) {
            pill.setBackgroundResource(R.drawable.bg_status_pill_warning);
            pill.setTextColor(0xFFFF8F00);
            return;
        }

        if (equalsAny(statusLabel, "Running")) {
            pill.setTextColor(ContextCompat.getColor(pill.getContext(), R.color.hg_primary));
            return;
        }

        pill.setTextColor(0xFF2E7D32);
    }

    private boolean equalsAny(String value, String... options) {
        if (value == null) return false;
        for (String o : options) {
            if (o != null && value.equalsIgnoreCase(o)) return true;
        }
        return false;
    }

    private String safeText(String s, String def) {
        if (s == null) return def;
        String t = s.trim();
        return t.isEmpty() ? def : t;
    }

    // =========================
    // ViewHolder
    // =========================
    static class SensorViewHolder extends RecyclerView.ViewHolder {

        TextView sensorNameText, sensorLocationText, sensorFlowText, sensorUsageText, sensorStatusText;
        TextView sensorLineText;
        Chip sensorCategoryChip;
        MaterialButton btnValveAction;

        public SensorViewHolder(@NonNull View itemView) {
            super(itemView);

            sensorNameText = itemView.findViewById(R.id.sensorNameText);
            sensorLocationText = itemView.findViewById(R.id.sensorLocationText);
            sensorFlowText = itemView.findViewById(R.id.sensorFlowText);
            sensorUsageText = itemView.findViewById(R.id.sensorUsageText);
            sensorStatusText = itemView.findViewById(R.id.sensorStatusText);

            sensorLineText = itemView.findViewById(R.id.sensorLineText);
            sensorCategoryChip = itemView.findViewById(R.id.sensorCategoryText);

            btnValveAction = itemView.findViewById(R.id.btnValveAction);
        }
    }
}
