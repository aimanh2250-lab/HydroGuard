package com.example.hydroguard;

public class InsightsDayModel {

    public String date;   // yyyy-MM-dd
    public float usage;   // liters

    public InsightsDayModel() {
        // Required for Firebase
    }

    public InsightsDayModel(String date, float usage) {
        this.date = (date != null) ? date : "-";
        this.usage = Math.max(0f, usage);
    }
}
