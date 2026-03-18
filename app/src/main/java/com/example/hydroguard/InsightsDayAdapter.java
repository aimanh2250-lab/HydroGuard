package com.example.hydroguard;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Locale;

public class InsightsDayAdapter extends RecyclerView.Adapter<InsightsDayAdapter.VH> {

    private final ArrayList<InsightsDayModel> list;

    public InsightsDayAdapter(ArrayList<InsightsDayModel> list) {
        this.list = (list != null) ? list : new ArrayList<>();
        setHasStableIds(true);
    }

    @Override
    public long getItemId(int position) {
        if (position < 0 || position >= list.size()) return RecyclerView.NO_ID;
        String d = list.get(position).date;
        return d != null ? d.hashCode() : RecyclerView.NO_ID;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_insights_day, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        if (position < 0 || position >= list.size()) return;

        InsightsDayModel m = list.get(position);
        if (m == null) {
            holder.dateText.setText("-");
            holder.usageText.setText("0.0 L");
            return;
        }

        holder.dateText.setText(m.date != null ? m.date : "-");
        holder.usageText.setText(String.format(Locale.getDefault(), "%.1f L", m.usage));
    }

    @Override
    public int getItemCount() {
        return list.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        TextView dateText, usageText;

        VH(@NonNull View itemView) {
            super(itemView);
            dateText = itemView.findViewById(R.id.dateText);
            usageText = itemView.findViewById(R.id.usageText);
        }
    }
}
