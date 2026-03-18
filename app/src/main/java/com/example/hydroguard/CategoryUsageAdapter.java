package com.example.hydroguard;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class CategoryUsageAdapter extends RecyclerView.Adapter<CategoryUsageAdapter.VH> {

    private final ArrayList<CategoryUsageRow> list = new ArrayList<>();

    public CategoryUsageAdapter(@NonNull List<CategoryUsageRow> initial) {
        if (initial != null) list.addAll(initial);
        setHasStableIds(true);
    }

    /** Refresh adapter without recreating it. */
    public void submitList(List<CategoryUsageRow> newList) {
        list.clear();
        if (newList != null) list.addAll(newList);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_category_usage, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        if (position < 0 || position >= list.size()) return;

        CategoryUsageRow row = list.get(position);

        String displayName = normalizeCategoryName(row != null ? row.name : null);
        float liters = sanitizeLiters(row != null ? row.liters : 0f);

        holder.tvCategoryName.setText(displayName);
        holder.tvCategoryLiters.setText(formatLiters(liters));
        holder.imgCategoryIcon.setImageResource(resolveIcon(displayName));
    }

    @Override
    public int getItemCount() {
        return list.size();
    }

    @Override
    public long getItemId(int position) {
        // ✅ Stable ID must match what the UI represents
        CategoryUsageRow row = (position >= 0 && position < list.size()) ? list.get(position) : null;
        String displayName = normalizeCategoryName(row != null ? row.name : null);
        return displayName.toLowerCase(Locale.ROOT).hashCode();
    }

    // =========================================================
    // Objective mapping: show only Tap / Shower / Uncategorized
    // =========================================================
    private String normalizeCategoryName(String raw) {
        if (raw == null) return "Uncategorized";
        String t = raw.trim();
        if (t.isEmpty()) return "Uncategorized";

        String n = t.toLowerCase(Locale.ROOT);

        // If Firebase uses ids like "tap"/"shower"/"uncategorized"
        if (n.equals("tap") || n.contains("tap")) return "Tap";
        if (n.equals("shower") || n.contains("shower")) return "Shower";
        if (n.equals("uncategorized") || n.contains("uncategor")) return "Uncategorized";

        // If someone accidentally passes "L1" / "L2" we still hide it and map safely
        if (n.equals("l1") || n.contains("line1") || n.contains("line 1")) return "Shower";
        if (n.equals("l2") || n.contains("line2") || n.contains("line 2")) return "Tap";

        // Otherwise show as-is (but still cleaned)
        return t;
    }

    private float sanitizeLiters(float v) {
        if (Float.isNaN(v) || Float.isInfinite(v)) return 0f;
        return Math.max(v, 0f);
    }

    private String formatLiters(float liters) {
        if (liters >= 100f) return String.format(Locale.getDefault(), "%.0f L", liters);
        return String.format(Locale.getDefault(), "%.1f L", liters);
    }

    @DrawableRes
    private int resolveIcon(String categoryName) {
        if (categoryName == null) return R.drawable.ic_device;

        String n = categoryName.toLowerCase(Locale.ROOT);

        if (n.contains("tap")) return R.drawable.ic_tap;
        if (n.contains("shower")) return R.drawable.ic_shower;
        if (n.contains("uncategor")) return R.drawable.ic_device;

        return R.drawable.ic_device;
    }

    // =========================================================
    // ViewHolder
    // =========================================================
    static class VH extends RecyclerView.ViewHolder {
        final ImageView imgCategoryIcon;
        final TextView tvCategoryName;
        final TextView tvCategoryLiters;

        VH(@NonNull View itemView) {
            super(itemView);
            imgCategoryIcon = itemView.findViewById(R.id.imgCategoryIcon);
            tvCategoryName = itemView.findViewById(R.id.tvCategoryName);
            tvCategoryLiters = itemView.findViewById(R.id.tvCategoryLiters);
        }
    }
}
