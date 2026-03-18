package com.example.hydroguard;

import androidx.annotation.NonNull;

public class CategoryUsageRow {
    public String name;
    public float liters;

    public CategoryUsageRow(String name, float liters) {
        this.name = name;
        this.liters = liters;
    }

    @NonNull
    @Override
    public String toString() {
        return "CategoryUsageRow{name='" + name + "', liters=" + liters + "}";
    }
}
