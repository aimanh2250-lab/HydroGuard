package com.example.hydroguard;

public class HistoryModel {

    public String date;
    public String usage;

    // "Tap 5.0 L • Shower 7.4 L ..."
    public String categoryBreakdown;

    // Always keep 24 elements
    public float[] hourly24 = new float[24];

    public HistoryModel() {
        // required for Firebase
    }

    public HistoryModel(String date, String usage) {
        this.date = date;
        this.usage = usage;
    }

    public HistoryModel(String date, String usage, String categoryBreakdown) {
        this.date = date;
        this.usage = usage;
        this.categoryBreakdown = categoryBreakdown;
    }

    public HistoryModel(String date, String usage, String categoryBreakdown, float[] hourly24) {
        this.date = date;
        this.usage = usage;
        this.categoryBreakdown = categoryBreakdown;

        if (hourly24 != null && hourly24.length == 24) {
            this.hourly24 = hourly24;
        } else {
            this.hourly24 = new float[24];
        }
    }

    public String getDate() { return date; }
    public String getUsage() { return usage; }
    public String getCategoryBreakdown() { return categoryBreakdown; }
    public float[] getHourly24() { return hourly24; }
}
