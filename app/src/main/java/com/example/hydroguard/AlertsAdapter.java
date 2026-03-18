package com.example.hydroguard;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.chip.Chip;

import java.util.ArrayList;
import java.util.Locale;

public class AlertsAdapter extends RecyclerView.Adapter<AlertsAdapter.AlertVH> {

    private final ArrayList<AlertModel> list;

    // ✅ FINAL mapping (internal only)
    private static final String LINE_SHOWER = "L1"; // Shower
    private static final String LINE_TAP    = "L2"; // Tap

    public AlertsAdapter(ArrayList<AlertModel> list) {
        this.list = (list != null) ? list : new ArrayList<>();
        setHasStableIds(true);
    }

    public void submitList(ArrayList<AlertModel> newList) {
        list.clear();
        if (newList != null) list.addAll(newList);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public AlertVH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_alert, parent, false);
        return new AlertVH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull AlertVH h, int position) {
        if (position < 0 || position >= list.size()) return;

        AlertModel a = list.get(position);
        if (a == null) return;

        String title = safe(a.title);
        String details = safe(a.details);
        String datetime = safe(a.datetime);

        h.alertTitleText.setText(title.isEmpty() ? "Alert" : title);
        h.alertDetailsText.setText(details.isEmpty() ? "-" : details);
        h.alertDateText.setText(datetime.isEmpty() ? "-" : datetime);

        Severity severity = resolveSeverity(a.severity);
        Tag tag = resolveTag(a.tag);

        // Severity UI
        if (h.severityBar != null) h.severityBar.setBackgroundColor(severity.color);
        h.alertTitleText.setTextColor(severity.color);

        // Device ID
        String deviceId = safeUpper(a.deviceId);
        if (deviceId.isEmpty()) deviceId = "HG001";

        // Source label (Tap/Shower) (NEVER show L1/L2)
        String sourceLabel = resolveSourceLabel(a);

        // Chip: TAG • HG001 • Tap/Shower
        String chipText = tag.label + " \u2022 " + deviceId;
        if (!sourceLabel.isEmpty()) chipText += " \u2022 " + sourceLabel;

        h.alertTag.setText(chipText);
        setChipColors(h.alertTag, tag.color);

        // Meta row: Tap/Shower • HG001
        if (h.alertMetaText != null) {
            String meta = sourceLabel.isEmpty() ? deviceId : (sourceLabel + " \u2022 " + deviceId);
            h.alertMetaText.setText(meta);
        }
    }

    @Override
    public int getItemCount() {
        return list.size();
    }

    // ✅ Stable IDs
    @Override
    public long getItemId(int position) {
        AlertModel a = list.get(position);
        if (a == null) return position;

        if (a.pushId != null && !a.pushId.trim().isEmpty()) {
            return stableId64(a.pushId.trim());
        }
        if (a.createdAt != null) return a.createdAt;
        return stableId64(safe(a.deviceId) + "_" + safe(a.title) + "_" + safe(a.datetime));
    }

    private long stableId64(@NonNull String s) {
        long h = 0xcbf29ce484222325L; // FNV-1a 64
        for (int i = 0; i < s.length(); i++) {
            h ^= s.charAt(i);
            h *= 0x100000001b3L;
        }
        return h;
    }

    // ===================== ViewHolder =====================
    static class AlertVH extends RecyclerView.ViewHolder {
        final View severityBar;
        final TextView alertTitleText, alertMetaText, alertDetailsText, alertDateText;
        final Chip alertTag;

        AlertVH(@NonNull View v) {
            super(v);
            severityBar = v.findViewById(R.id.severityBar);
            alertTitleText = v.findViewById(R.id.alertTitleText);
            alertMetaText = v.findViewById(R.id.alertMetaText);
            alertDetailsText = v.findViewById(R.id.alertDetailsText);
            alertDateText = v.findViewById(R.id.alertDateText);
            alertTag = v.findViewById(R.id.alertTag);
        }
    }

    // ===================== Strict Option A enums =====================

    enum Severity {
        GRAY(Color.parseColor("#9E9E9E")),
        GREEN(Color.parseColor("#2E7D32")),
        ORANGE(Color.parseColor("#FB8C00")),
        RED(Color.parseColor("#D32F2F"));

        final int color;
        Severity(int c) { this.color = c; }
    }

    enum Tag {
        LEAK("LEAK", Color.parseColor("#C62828")),
        LIMIT("LIMIT", Color.parseColor("#D32F2F")),
        VALVE("VALVE", Color.parseColor("#1565C0")), // 🔥 ADD
        SYSTEM("SYSTEM", Color.parseColor("#455A64"));

        final String label;
        final int color;
        Tag(String l, int c) { label = l; color = c; }
    }


    private Severity resolveSeverity(String s) {
        String v = safeUpper(s);
        if ("RED".equals(v)) return Severity.RED;
        if ("ORANGE".equals(v)) return Severity.ORANGE;
        if ("GREEN".equals(v)) return Severity.GREEN;
        return Severity.GRAY;
    }

    private Tag resolveTag(String t) {
        String v = safeUpper(t);
        if ("LEAK".equals(v)) return Tag.LEAK;
        if ("LIMIT".equals(v)) return Tag.LIMIT;
        if ("VALVE".equals(v)) return Tag.VALVE;
        return Tag.SYSTEM;
    }

    // ===================== Source mapping (Objective aligned) =====================

    private String resolveSourceLabel(@NonNull AlertModel a) {
        // 1) Best: source field from ESP/app
        String s = safeLower(a.source);
        if ("tap".equals(s)) return "Tap";
        if ("shower".equals(s)) return "Shower";

        // 2) Derive from lineId (never display L1/L2)
        String line = safeUpper(a.lineId);
        if (LINE_TAP.equals(line)) return "Tap";
        if (LINE_SHOWER.equals(line)) return "Shower";

        return "";
    }

    // ===================== Chip styling =====================

    private void setChipColors(Chip chip, int baseColor) {
        if (chip == null) return;
        chip.setChipBackgroundColor(ColorStateList.valueOf(baseColor));
        chip.setTextColor(Color.WHITE);
        chip.setChipStrokeWidth(1f);
        chip.setChipStrokeColor(ColorStateList.valueOf(adjustAlpha(Color.WHITE, 0.35f)));
        chip.setChipIconTint(ColorStateList.valueOf(Color.WHITE));
    }

    private int adjustAlpha(int color, float factor) {
        return Color.argb(
                Math.round(Color.alpha(color) * factor),
                Color.red(color),
                Color.green(color),
                Color.blue(color)
        );
    }

    // ===================== Helpers =====================

    private String safe(String s) {
        return s == null ? "" : s.trim();
    }

    private String safeLower(String s) {
        return safe(s).toLowerCase(Locale.ROOT);
    }

    private String safeUpper(String s) {
        return safe(s).toUpperCase(Locale.ROOT);
    }
}
