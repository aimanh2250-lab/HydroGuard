package com.example.hydroguard;

import androidx.annotation.NonNull;

import java.util.Locale;

public class BillingDayModel {

    public String date;

    // ✅ Usage breakdown
    public float tapLiters;
    public float showerLiters;
    public float uncategorizedLiters;

    // Billing
    public float totalLiters;
    public float cost;        // ✅ allocated share of monthly cost
    public String blockInfo;  // ✅ cumulative month tier at this day

    public BillingDayModel() { }

    public BillingDayModel(String date,
                           float tapLiters,
                           float showerLiters,
                           float uncategorizedLiters,
                           float totalLiters,
                           float cost,
                           String blockInfo) {

        this.date = safeString(date);
        this.tapLiters = safeFloat(tapLiters);
        this.showerLiters = safeFloat(showerLiters);
        this.uncategorizedLiters = safeFloat(uncategorizedLiters);

        float computed = this.tapLiters + this.showerLiters + this.uncategorizedLiters;
        this.totalLiters = (totalLiters > 0f) ? safeFloat(totalLiters) : computed;

        this.cost = safeFloat(cost);
        this.blockInfo = safeString(blockInfo);
    }

    public float getComputedTotal() {
        return safeFloat(tapLiters) + safeFloat(showerLiters) + safeFloat(uncategorizedLiters);
    }

    public boolean hasUncategorized() {
        return safeFloat(uncategorizedLiters) > 0.5f;
    }

    @NonNull
    public String getSafeBlockInfo() {
        return (blockInfo != null && !blockInfo.trim().isEmpty()) ? blockInfo.trim() : "-";
    }

    @NonNull
    public String getSafeDate() {
        return (date != null && !date.trim().isEmpty()) ? date.trim() : "-";
    }

    @NonNull
    public String formatTapLine() {
        return String.format(Locale.getDefault(), "Tap: %.1f L", safeFloat(tapLiters));
    }

    @NonNull
    public String formatShowerLine() {
        return String.format(Locale.getDefault(), "Shower: %.1f L", safeFloat(showerLiters));
    }

    private static float safeFloat(float v) {
        if (Float.isNaN(v) || Float.isInfinite(v)) return 0f;
        return Math.max(0f, v);
    }

    private static String safeString(String s) {
        return (s != null) ? s.trim() : "";
    }
}
