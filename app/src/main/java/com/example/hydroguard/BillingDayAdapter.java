package com.example.hydroguard;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.chip.Chip;

import java.util.ArrayList;
import java.util.Locale;

public class BillingDayAdapter extends RecyclerView.Adapter<BillingDayAdapter.BillingViewHolder> {

    private final ArrayList<BillingDayModel> list;

    public BillingDayAdapter(ArrayList<BillingDayModel> list) {
        this.list = (list != null) ? list : new ArrayList<>();
        setHasStableIds(true);
    }

    @NonNull
    @Override
    public BillingViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_billing_day, parent, false);
        return new BillingViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull BillingViewHolder holder, int position) {
        if (position < 0 || position >= list.size()) return;

        BillingDayModel m = list.get(position);
        if (m == null) return;

        String date = (m.date != null) ? m.date : "-";

        float tap = sanitize(m.tapLiters);
        float shower = sanitize(m.showerLiters);
        float uncat = sanitize(m.uncategorizedLiters);
        float total = sanitize(m.totalLiters);
        float cost = sanitize(m.cost);

        String tier = (m.blockInfo != null && !m.blockInfo.trim().isEmpty())
                ? m.blockInfo.trim()
                : "-";

        holder.billingDateText.setText(date);

        holder.billingTapText.setText(String.format(Locale.getDefault(), "Tap: %.1f L", tap));
        holder.billingShowerText.setText(String.format(Locale.getDefault(), "Shower: %.1f L", shower));

        if (uncat > 0.5f) {
            holder.billingUncatText.setVisibility(View.VISIBLE);
            holder.billingUncatText.setText(String.format(Locale.getDefault(), "Uncategorized: %.1f L", uncat));
        } else {
            holder.billingUncatText.setVisibility(View.GONE);
        }

        holder.billingTotalText.setText(String.format(Locale.getDefault(), "Total: %.1f L", total));
        holder.billingBlockText.setText(tier);

        // ✅ This RM is allocated share of MONTHLY bill (objective-correct)
        holder.chipCost.setText(String.format(Locale.getDefault(), "RM %.2f", cost));
    }

    @Override
    public int getItemCount() {
        return list.size();
    }

    @Override
    public long getItemId(int position) {
        BillingDayModel m = (position >= 0 && position < list.size()) ? list.get(position) : null;
        String key = (m != null && m.date != null) ? m.date : ("pos_" + position);
        return key.hashCode();
    }

    private float sanitize(float v) {
        if (Float.isNaN(v) || Float.isInfinite(v) || v < 0f) return 0f;
        return v;
    }

    static class BillingViewHolder extends RecyclerView.ViewHolder {

        final TextView billingDateText;
        final Chip chipCost;

        final TextView billingTapText;
        final TextView billingShowerText;
        final TextView billingUncatText;

        final TextView billingTotalText;
        final TextView billingBlockText;

        BillingViewHolder(@NonNull View itemView) {
            super(itemView);

            billingDateText = itemView.findViewById(R.id.billingDateText);
            chipCost = itemView.findViewById(R.id.chipCost);

            billingTapText = itemView.findViewById(R.id.billingTapText);
            billingShowerText = itemView.findViewById(R.id.billingShowerText);
            billingUncatText = itemView.findViewById(R.id.billingUncatText);

            billingTotalText = itemView.findViewById(R.id.billingTotalText);
            billingBlockText = itemView.findViewById(R.id.billingBlockText);
        }
    }
}
