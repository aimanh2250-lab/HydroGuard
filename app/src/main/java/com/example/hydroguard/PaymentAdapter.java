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

public class PaymentAdapter extends RecyclerView.Adapter<PaymentAdapter.VH> {

    private final ArrayList<PaymentModel> list;

    public PaymentAdapter(ArrayList<PaymentModel> list) {
        this.list = list;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_payment, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        PaymentModel p = list.get(position);

        h.tvMonth.setText(p.month != null && !p.month.isEmpty() ? p.month : "Payment");
        String meta = (p.method == null ? "-" : p.method) + " • " + (p.timestamp == null ? "-" : p.timestamp);
        h.tvMeta.setText(meta);

        h.tvAmount.setText(String.format(Locale.getDefault(), "RM %.2f", p.amount));
        h.chipStatus.setText(p.status != null ? p.status : "UNKNOWN");
    }

    @Override
    public int getItemCount() {
        return list.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvMonth, tvMeta, tvAmount;
        Chip chipStatus;

        VH(@NonNull View itemView) {
            super(itemView);
            tvMonth = itemView.findViewById(R.id.tvPayMonth);
            tvMeta = itemView.findViewById(R.id.tvPayMeta);
            tvAmount = itemView.findViewById(R.id.tvPayAmount);
            chipStatus = itemView.findViewById(R.id.chipPayStatus);
        }
    }
}
