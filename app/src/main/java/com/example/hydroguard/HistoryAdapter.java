package com.example.hydroguard;

import android.app.AlertDialog;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.google.android.material.chip.Chip;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.FirebaseDatabase;

import java.util.ArrayList;
import java.util.Locale;

public class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.HistoryViewHolder> {

    private final ArrayList<HistoryModel> list;

    private static final float THRESH_NORMAL_MAX = 10f; // <= 10L
    private static final float THRESH_HIGH_MAX   = 20f; // 10-20L
    // >20L => danger

    public HistoryAdapter(ArrayList<HistoryModel> list) {
        this.list = (list != null) ? list : new ArrayList<>();
        setHasStableIds(true);
    }

    @Override
    public long getItemId(int position) {
        HistoryModel m = list.get(position);
        return m != null && m.getDate() != null
                ? m.getDate().hashCode() ^ position
                : position;
    }

    @NonNull
    @Override
    public HistoryViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_history, parent, false);
        return new HistoryViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull HistoryViewHolder holder, int position) {

        HistoryModel model = list.get(position);
        if (model == null) return;

        String date = (model.getDate() != null) ? model.getDate() : "-";
        float usageLiters = parseUsage(model.getUsage());

        holder.dateText.setText(date);

        if (holder.usageChip != null) {
            holder.usageChip.setText(String.format(Locale.getDefault(), "%.1f L", usageLiters));
            styleUsageChip(holder.usageChip, usageLiters);
        }

        if (holder.categoryBreakdown != null) {
            String breakdown = model.getCategoryBreakdown();
            if (breakdown != null && !breakdown.trim().isEmpty()) {
                holder.categoryBreakdown.setVisibility(View.VISIBLE);
                holder.categoryBreakdown.setText(breakdown);
            } else {
                holder.categoryBreakdown.setVisibility(View.GONE);
            }
        }

        if (holder.dayChart != null && !holder.chartInitialized) {
            initMiniChart(holder.dayChart);
            holder.chartInitialized = true;
        }

        if (holder.dayChart != null) {
            float[] hourly = model.getHourly24();
            ArrayList<Entry> entries = new ArrayList<>(24);

            for (int h = 0; h < 24; h++) {
                float v = (hourly != null && hourly.length == 24) ? hourly[h] : 0f;
                entries.add(new Entry(h, v));
            }
            setMiniChartData(holder.dayChart, entries);
        }

        if (holder.btnDelete != null) {
            holder.btnDelete.setOnClickListener(v -> {
                int pos = holder.getAdapterPosition();
                if (pos == RecyclerView.NO_POSITION) return;
                HistoryModel m = list.get(pos);
                showDeleteDialog(v, m, pos);
            });
        }
    }

    @Override
    public int getItemCount() { return list.size(); }

    private void styleUsageChip(@NonNull Chip chip, float liters) {
        if (liters > THRESH_HIGH_MAX) {
            chip.setChipBackgroundColorResource(R.color.hg_errorContainer);
            chip.setTextColor(ContextCompat.getColor(chip.getContext(), R.color.hg_error));
            chip.setChipIconResource(R.drawable.ic_warning);
        } else if (liters > THRESH_NORMAL_MAX) {
            chip.setChipBackgroundColorResource(R.color.hg_warningContainer);
            chip.setTextColor(ContextCompat.getColor(chip.getContext(), R.color.hg_warning));
            chip.setChipIconResource(R.drawable.ic_warning);
        } else {
            chip.setChipBackgroundColorResource(R.color.hg_primaryContainer);
            chip.setTextColor(ContextCompat.getColor(chip.getContext(), R.color.hg_primary));
            chip.setChipIconResource(R.drawable.ic_flow);
        }
    }

    private void initMiniChart(LineChart chart) {
        chart.getLegend().setEnabled(false);
        chart.getDescription().setEnabled(false);
        chart.setTouchEnabled(false);
        chart.setDragEnabled(false);
        chart.setScaleEnabled(false);

        chart.getAxisRight().setEnabled(false);
        chart.getAxisLeft().setEnabled(false);

        XAxis x = chart.getXAxis();
        x.setEnabled(false);

        chart.setViewPortOffsets(0f, 0f, 0f, 0f);
    }

    private void setMiniChartData(LineChart chart, ArrayList<Entry> entries) {
        LineDataSet ds = new LineDataSet(entries, "");
        ds.setDrawValues(false);
        ds.setDrawCircles(false);
        ds.setLineWidth(2f);
        ds.setMode(LineDataSet.Mode.CUBIC_BEZIER);

        chart.setData(new LineData(ds));
        chart.invalidate();
    }

    private void showDeleteDialog(View v, HistoryModel model, int adapterPos) {
        String date = (model != null && model.getDate() != null) ? model.getDate() : "this day";

        new AlertDialog.Builder(v.getContext())
                .setTitle("Delete History")
                .setMessage("Delete record for " + date + "?")
                .setPositiveButton("Delete", (dialog, which) -> deleteFromFirebase(v, model, adapterPos))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void deleteFromFirebase(View v, HistoryModel model, int adapterPos) {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) {
            Toast.makeText(v.getContext(), "Please login first", Toast.LENGTH_SHORT).show();
            return;
        }

        String dateKey = (model != null) ? model.getDate() : null;
        if (dateKey == null || dateKey.trim().isEmpty()) {
            Toast.makeText(v.getContext(), "Invalid date key", Toast.LENGTH_SHORT).show();
            return;
        }

        FirebaseDatabase.getInstance()
                .getReference()
                .child("hydroguard")
                .child("historyDaily")
                .child(uid)
                .child(dateKey)
                .removeValue()
                .addOnSuccessListener(unused -> {
                    if (adapterPos >= 0 && adapterPos < list.size()) {
                        list.remove(adapterPos);
                        notifyItemRemoved(adapterPos);
                    } else {
                        notifyDataSetChanged();
                    }
                    Toast.makeText(v.getContext(), "Deleted", Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e ->
                        Toast.makeText(v.getContext(), e.getMessage(), Toast.LENGTH_SHORT).show()
                );
    }

    private float parseUsage(String s) {
        try { return Float.parseFloat(s); }
        catch (Exception e) { return 0f; }
    }

    static class HistoryViewHolder extends RecyclerView.ViewHolder {
        TextView dateText;
        Chip usageChip;

        LineChart dayChart;
        TextView categoryBreakdown;
        ImageButton btnDelete;

        boolean chartInitialized = false;

        public HistoryViewHolder(@NonNull View itemView) {
            super(itemView);
            dateText = itemView.findViewById(R.id.historyDate);
            usageChip = itemView.findViewById(R.id.historyUsage);
            dayChart = itemView.findViewById(R.id.historyDayChart);
            categoryBreakdown = itemView.findViewById(R.id.categoryBreakdown);
            btnDelete = itemView.findViewById(R.id.btnDelete);
        }
    }
}
