package com.example.hydroguard;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Spinner;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.LimitLine;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.android.material.datepicker.MaterialDatePicker.Builder;

import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

public class HistoryActivity extends BaseDrawerActivity {

    private static final String TAG = "HistoryActivity";
    private boolean periodInitialized = false;
    private boolean isSyncingPeriod = false;


    private RecyclerView historyRecycler;
    private LineChart usageChart;

    private android.widget.RadioGroup periodGroup;
    private ChipGroup chipGroupPeriod;
    private Spinner spinnerCategory;

    private View pickerCard, pickerRow;
    private MaterialButton btnPickWeek, btnPickMonth;

    private final ArrayList<HistoryModel> list = new ArrayList<>();
    private HistoryAdapter adapter;

    private final SimpleDateFormat sdf =
            new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());

    private static class CategoryOption {
        String id, name;
        CategoryOption(String id, String name) {
            this.id = id;
            this.name = name;
        }
    }

    private final ArrayList<CategoryOption> categoryOptions = new ArrayList<>();
    private ArrayAdapter<String> categoryAdapter;
    private String selectedCategoryId = "all";

    private Calendar selectedWeekDate;
    private Calendar selectedMonthDate;

    private static class DayDoc {
        float total;
        LinkedHashMap<String, Float> categories = new LinkedHashMap<>();
        float[] hourly24 = new float[24];
    }

    private final LinkedHashMap<String, DayDoc> dayDocs = new LinkedHashMap<>();
    private final LinkedHashMap<String, String> catIdToName = new LinkedHashMap<>();

    // Firebase
    private DatabaseReference historyDailyRef;
    private ValueEventListener historyDailyListener;

    @Override
    protected int getLayoutResId() {
        return R.layout.activity_history;
    }

    @Override
    protected int getCurrentNavItemId() {
        return R.id.nav_history;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        historyRecycler = findViewById(R.id.historyRecycler);
        usageChart = findViewById(R.id.usageChart);
        periodGroup = findViewById(R.id.periodGroup);
        chipGroupPeriod = findViewById(R.id.chipGroupPeriod);
        spinnerCategory = findViewById(R.id.spinnerCategory);

        pickerCard = findViewById(R.id.pickerCard);
        pickerRow = findViewById(R.id.pickerRow);
        btnPickWeek = findViewById(R.id.btnPickWeek);
        btnPickMonth = findViewById(R.id.btnPickMonth);

        historyRecycler.setLayoutManager(new LinearLayoutManager(this));
        adapter = new HistoryAdapter(list);
        historyRecycler.setAdapter(adapter);

        setupChartAppearance();
        setupCategorySpinner();
        setupPickerUI();

        loadCategoriesThenHistoryDaily();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        detachHistoryListener();
    }

    private void detachHistoryListener() {
        if (historyDailyRef != null && historyDailyListener != null) {
            historyDailyRef.removeEventListener(historyDailyListener);
        }
        historyDailyListener = null;
        historyDailyRef = null;
    }

    private void setupChartAppearance() {
        usageChart.getDescription().setEnabled(false);
        usageChart.setNoDataText("No usage data yet.");
        usageChart.setTouchEnabled(true);
        usageChart.setPinchZoom(true);
        usageChart.getAxisRight().setEnabled(false);

        XAxis x = usageChart.getXAxis();
        x.setPosition(XAxis.XAxisPosition.BOTTOM);
        x.setGranularity(1f);

        YAxis left = usageChart.getAxisLeft();
        left.setAxisMinimum(0f);
    }

    private void setupCategorySpinner() {
        categoryAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_dropdown_item,
                new ArrayList<>()
        );
        spinnerCategory.setAdapter(categoryAdapter);

        spinnerCategory.setOnItemSelectedListener(
                new android.widget.AdapterView.OnItemSelectedListener() {
                    @Override
                    public void onItemSelected(
                            android.widget.AdapterView<?> parent,
                            View view, int position, long id) {

                        if (position < 0 || position >= categoryOptions.size()) return;
                        selectedCategoryId = categoryOptions.get(position).id;
                        rebuildListFromDayDocs();
                        renderChartByCurrentPeriod();
                    }

                    @Override public void onNothingSelected(android.widget.AdapterView<?> parent) {}
                });
    }

    private void loadCategoriesThenHistoryDaily() {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) return;

        categoryOptions.clear();
        catIdToName.clear();

        categoryOptions.add(new CategoryOption("all", "All Categories"));
        categoryOptions.add(new CategoryOption("tap", "Tap"));
        categoryOptions.add(new CategoryOption("shower", "Shower"));
        categoryOptions.add(new CategoryOption("uncategorized", "Uncategorized"));

        catIdToName.put("tap", "Tap");
        catIdToName.put("shower", "Shower");
        catIdToName.put("uncategorized", "Uncategorized");

        refreshCategorySpinnerUI();
        attachHistoryDailyListener(uid);
    }

    private void refreshCategorySpinnerUI() {
        ArrayList<String> labels = new ArrayList<>();
        for (CategoryOption o : categoryOptions) labels.add(o.name);
        categoryAdapter.clear();
        categoryAdapter.addAll(labels);
        categoryAdapter.notifyDataSetChanged();
        spinnerCategory.setSelection(0);
    }

    private void attachHistoryDailyListener(String uid) {
        detachHistoryListener();

        historyDailyRef = FirebaseDatabase.getInstance()
                .getReference()
                .child("hydroguard")
                .child("historyDaily")
                .child(uid);

        historyDailyListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                dayDocs.clear();

                for (DataSnapshot daySnap : snapshot.getChildren()) {
                    String dateKey = daySnap.getKey();
                    if (dateKey == null) continue;

                    DayDoc doc = new DayDoc();
                    doc.total = parseFloatSafe(daySnap.child("totalLiters").getValue());

                    for (int i = 0; i < 24; i++) doc.hourly24[i] = 0f;

                    DataSnapshot catSnap = daySnap.child("categories");
                    for (DataSnapshot c : catSnap.getChildren()) {
                        String catId = c.getKey();
                        if (catId == null) continue;
                        doc.categories.put(catId, parseFloatSafe(c.getValue()));
                    }

                    DataSnapshot hourlySnap = daySnap.child("hourly");
                    for (DataSnapshot h : hourlySnap.getChildren()) {
                        int hour = safeParseHour(h.getKey());
                        if (hour >= 0 && hour < 24) {
                            doc.hourly24[hour] = parseFloatSafe(h.getValue());
                        }
                    }

                    if (doc.total <= 0f) {
                        float sum = 0f;
                        for (Float v : doc.categories.values()) sum += v;
                        doc.total = sum;
                    }

                    dayDocs.put(dateKey, doc);
                }

                rebuildListFromDayDocs();

                if (!periodInitialized) {
                    periodGroup.check(R.id.rbDaily);
                    chipGroupPeriod.check(R.id.chipDaily);
                    periodInitialized = true;
                }

                renderChartByCurrentPeriod();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, error.getMessage(), error.toException());
            }
        };

        historyDailyRef
                .limitToLast(60)
                .addValueEventListener(historyDailyListener);
    }

    private void rebuildListFromDayDocs() {
        list.clear();

        for (Map.Entry<String, DayDoc> e : dayDocs.entrySet()) {
            String date = e.getKey();
            DayDoc doc = e.getValue();

            float liters = "all".equals(selectedCategoryId)
                    ? doc.total
                    : doc.categories.getOrDefault(selectedCategoryId, 0f);

            if ("all".equals(selectedCategoryId) || liters > 0f) {
                list.add(new HistoryModel(
                        date,
                        String.format(Locale.getDefault(), "%.2f", liters),
                        "",
                        doc.hourly24
                ));
            }
        }

        Collections.sort(list, (a, b) -> b.date.compareTo(a.date));
        adapter.notifyDataSetChanged();
    }

    private void renderChartByCurrentPeriod() {
        int chipId = chipGroupPeriod.getCheckedChipId();

        if (chipId == R.id.chipWeekly) {
            showWeeklyChart();
        }
        else if (chipId == R.id.chipMonthly) {
            showMonthlyChart();
        }
        else {
            showDailyChart();
        }
    }

    private void showDailyChart() {

        if (dayDocs.isEmpty()) {
            usageChart.setData(null);
            usageChart.setNoDataText("No daily usage data");
            usageChart.invalidate();
            return;
        }

        ArrayList<Entry> entries = new ArrayList<>();
        ArrayList<String> labels = new ArrayList<>();

        int i = 0;
        for (Map.Entry<String, DayDoc> e : dayDocs.entrySet()) {

            float value;
            if ("all".equals(selectedCategoryId)) {
                value = e.getValue().total;
            } else {
                value = e.getValue()
                        .categories
                        .getOrDefault(selectedCategoryId, 0f);
            }

            entries.add(new Entry(i++, value));
            labels.add(e.getKey().substring(5));
        }

        applyDataToChart(entries, labels, "Daily Usage");
    }


    private void showWeeklyChart() {

        if (selectedWeekDate == null) {
            selectedWeekDate = getLatestDateFromData(); // ✅ IMPORTANT
        }


        Calendar start = (Calendar) selectedWeekDate.clone();
        start.set(Calendar.DAY_OF_WEEK, start.getFirstDayOfWeek());
        zeroTime(start);

        Calendar end = (Calendar) start.clone();
        end.add(Calendar.DAY_OF_YEAR, 7);

        TreeMap<String, Float> map = new TreeMap<>();

        for (Map.Entry<String, DayDoc> e : dayDocs.entrySet()) {
            try {
                Calendar d = Calendar.getInstance();
                d.setTime(sdf.parse(e.getKey()));

                if (!d.before(start) && d.before(end)) {

                    float value;
                    if ("all".equals(selectedCategoryId)) {
                        value = e.getValue().total;
                    } else {
                        value = e.getValue()
                                .categories
                                .getOrDefault(selectedCategoryId, 0f);
                    }

                    map.put(e.getKey(), value);

                }
            } catch (Exception ignored) {}
        }

        renderMap(map, "Weekly Usage");
    }

    private void showMonthlyChart() {
        if (selectedMonthDate == null) {
            selectedMonthDate = getLatestDateFromData(); // ✅ IMPORTANT
        }

        String monthKey = new SimpleDateFormat("yyyy-MM", Locale.getDefault())
                .format(selectedMonthDate.getTime());

        TreeMap<String, Float> map = new TreeMap<>();

        for (Map.Entry<String, DayDoc> e : dayDocs.entrySet()) {
            if (e.getKey().startsWith(monthKey)) {

                float value;
                if ("all".equals(selectedCategoryId)) {
                    value = e.getValue().total;
                } else {
                    value = e.getValue()
                            .categories
                            .getOrDefault(selectedCategoryId, 0f);
                }

                map.put(e.getKey(), value);

            }
        }

        renderMap(map, "Monthly Usage");
    }

    private void renderMap(TreeMap<String, Float> map, String label) {

        if (map.isEmpty()) {
            usageChart.setData(null);
            usageChart.setNoDataText("No data for this week/month");
            usageChart.invalidate();
            return;
        }

        ArrayList<Entry> entries = new ArrayList<>();
        ArrayList<String> labels = new ArrayList<>();

        int i = 0;
        for (Map.Entry<String, Float> e : map.entrySet()) {
            entries.add(new Entry(i++, e.getValue()));
            labels.add(e.getKey().substring(5));
        }

        applyDataToChart(entries, labels, label);
    }


    private void applyDataToChart(
            ArrayList<Entry> entries,
            ArrayList<String> labels,
            String label) {

        // -----------------------
        // EMPTY STATE
        // -----------------------
        if (entries.isEmpty()) {
            usageChart.setData(null);
            usageChart.setNoDataText("No usage for selected category & period");
            usageChart.invalidate();
            return;
        }

        // -----------------------
        // FIND MAX Y
        // -----------------------
        float tmpMax = 0f;
        for (Entry e : entries) tmpMax = Math.max(tmpMax, e.getY());
        final float maxValue = tmpMax;

        // -----------------------
        // DATASET
        // -----------------------
        LineDataSet ds = new LineDataSet(entries, label);
        ds.setDrawValues(false);
        ds.setDrawCircles(true);
        ds.setLineWidth(3f);
        ds.setCircleRadius(4f);
        ds.setMode(LineDataSet.Mode.CUBIC_BEZIER);
        ds.setColor(0xFF1976D2);
        ds.setCircleColor(0xFF1976D2);

        usageChart.getAxisLeft().removeAllLimitLines();

        // -----------------------
        // X AXIS CONFIG
        // -----------------------
        XAxis xAxis = usageChart.getXAxis();
        xAxis.setValueFormatter(new IndexAxisValueFormatter(labels));
        xAxis.setGranularity(1f);
        xAxis.setDrawGridLines(false);
        xAxis.setAvoidFirstLastClipping(true);

        int labelCount = labels.size();
        if (labelCount > 10) {
            xAxis.setLabelRotationAngle(-60f);
            xAxis.setLabelCount(7, false);
        } else if (labelCount > 5) {
            xAxis.setLabelRotationAngle(-45f);
            xAxis.setLabelCount(labelCount, false);
        } else {
            xAxis.setLabelRotationAngle(-30f);
            xAxis.setLabelCount(labelCount, false);
        }

        // -----------------------
        // DRAW CHART FIRST (IMPORTANT)
        // -----------------------
        usageChart.setData(new LineData(ds));
        forceYAxis(maxValue);
        usageChart.invalidate();

        // -----------------------
        // PERIOD-BASED ANIMATION
        // -----------------------
        if (label.contains("Daily")) {
            usageChart.animateX(600);
        } else if (label.contains("Weekly")) {
            usageChart.animateY(600);
        } else {
            usageChart.animateXY(600, 600);
        }

        // -----------------------
        // OPTIONAL LIMIT LINES
        // -----------------------
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) return;

        FirebaseDatabase.getInstance()
                .getReference()
                .child("hydroguard")
                .child("settings")
                .child(uid)
                .get()
                .addOnSuccessListener(snapshot -> {

                    if (!snapshot.exists()) return;

                    float dailyLimit =
                            parseFloatSafe(snapshot.child("dailyLimit").getValue());
                    float monthlyLimit =
                            parseFloatSafe(snapshot.child("monthlyLimit").getValue());

                    float activeLimit = 0f;
                    if (label.contains("Daily"))   activeLimit = dailyLimit;
                    if (label.contains("Monthly")) activeLimit = monthlyLimit;

                    if (activeLimit > 0f) {
                        addLimitLine(activeLimit);

                        if (maxValue > activeLimit) {
                            ds.setColor(0xFFD32F2F);
                            ds.setCircleColor(0xFFD32F2F);
                        }

                        usageChart.invalidate();
                    }
                });
    }

    private void addLimitLine(float value) {
        LimitLine limit = new LimitLine(value, "Limit " + value + " L");
        limit.setLineColor(0xFFD32F2F);
        limit.setLineWidth(2f);
        limit.enableDashedLine(12f, 8f, 0);
        limit.setTextSize(12f);
        usageChart.getAxisLeft().addLimitLine(limit);
    }

    private void forceYAxis(float maxValue) {
        YAxis left = usageChart.getAxisLeft();
        left.setAxisMaximum(Math.max(1f, maxValue * 1.2f));
    }

    private float parseUsage(String s) {
        try { return Float.parseFloat(s); }
        catch (Exception e) { return 0f; }
    }

    private float parseFloatSafe(Object v) {
        try {
            if (v instanceof Number) return ((Number) v).floatValue();
            if (v instanceof String) return Float.parseFloat(((String) v).trim());
        } catch (Exception ignored) {}
        return 0f;
    }

    private int safeParseHour(String h) {
        try { return Integer.parseInt(h); }
        catch (Exception ignored) { return 0; }
    }

    private void setupPickerUI() {

        // Default state
        if (periodGroup != null) periodGroup.check(R.id.rbDaily);
        if (chipGroupPeriod != null) chipGroupPeriod.check(R.id.chipDaily);

        // -------------------------
        // CHIP → RADIO (main logic)
        // -------------------------
        if (chipGroupPeriod != null) {
            chipGroupPeriod.setOnCheckedChangeListener((group, checkedId) -> {

                if (checkedId == R.id.chipDaily) {
                    periodGroup.check(R.id.rbDaily);
                    hidePickers();
                    renderChartByCurrentPeriod();
                }

                else if (checkedId == R.id.chipWeekly) {
                    periodGroup.check(R.id.rbWeekly);

                    if (selectedWeekDate == null)
                        selectedWeekDate = getLatestDateFromData(); // ✅ KEEP LATEST DATA

                    showWeekPicker();
                    renderChartByCurrentPeriod();
                }


                else if (checkedId == R.id.chipMonthly) {
                    periodGroup.check(R.id.rbMonthly);

                    if (selectedMonthDate == null)
                        selectedMonthDate = getLatestDateFromData(); // ✅ KEEP LATEST DATA

                    showMonthPicker();
                    renderChartByCurrentPeriod();
                }

            });
        }

        // -------------------------
        // RADIO → CHIP (SYNC ONLY)
        // -------------------------
        if (periodGroup != null && chipGroupPeriod != null) {
            periodGroup.setOnCheckedChangeListener((group, checkedId) -> {

                if (isSyncingPeriod) return;
                isSyncingPeriod = true;

                if (checkedId == R.id.rbDaily) {
                    chipGroupPeriod.check(R.id.chipDaily);
                }
                else if (checkedId == R.id.rbWeekly) {
                    chipGroupPeriod.check(R.id.chipWeekly);
                }
                else if (checkedId == R.id.rbMonthly) {
                    chipGroupPeriod.check(R.id.chipMonthly);
                }

                isSyncingPeriod = false;
            });
        }

        // -------------------------
        // PICKER BUTTONS
        // -------------------------
        if (btnPickWeek != null) {
            btnPickWeek.setOnClickListener(v -> openWeekPicker());
        }

        if (btnPickMonth != null) {
            btnPickMonth.setOnClickListener(v -> openMonthPicker());
        }
    }


    private void hidePickers() {
        pickerCard.setVisibility(View.GONE);
        pickerRow.setVisibility(View.GONE);
        btnPickWeek.setVisibility(View.GONE);
        btnPickMonth.setVisibility(View.GONE);
    }

    private void showWeekPicker() {
        pickerCard.setVisibility(View.VISIBLE);
        pickerRow.setVisibility(View.VISIBLE);
        btnPickWeek.setVisibility(View.VISIBLE);
        btnPickMonth.setVisibility(View.GONE);

        // ❌ DO NOT set selectedWeekDate here

        renderChartByCurrentPeriod();
    }


    private void showMonthPicker() {
        pickerCard.setVisibility(View.VISIBLE);
        pickerRow.setVisibility(View.VISIBLE);
        btnPickWeek.setVisibility(View.GONE);
        btnPickMonth.setVisibility(View.VISIBLE);

        // ❌ DO NOT set selectedMonthDate here

        renderChartByCurrentPeriod();
    }


    private void openWeekPicker() {
        MaterialDatePicker<Long> picker =
                MaterialDatePicker.Builder.datePicker()
                        .setTitleText("Select week")
                        .build();

        picker.show(getSupportFragmentManager(), "WEEK_PICKER");

        picker.addOnPositiveButtonClickListener(selection -> {
            Calendar cal = Calendar.getInstance();
            cal.setTimeInMillis(selection);

            cal.set(Calendar.DAY_OF_WEEK, cal.getFirstDayOfWeek());
            zeroTime(cal);

            selectedWeekDate = cal;
            renderChartByCurrentPeriod();
        });
    }
    private void openMonthPicker() {
        MaterialDatePicker<Long> picker =
                MaterialDatePicker.Builder.datePicker()
                        .setTitleText("Select month")
                        .build();

        picker.show(getSupportFragmentManager(), "MONTH_PICKER");

        picker.addOnPositiveButtonClickListener(selection -> {
            Calendar cal = Calendar.getInstance();
            cal.setTimeInMillis(selection);

            cal.set(Calendar.DAY_OF_MONTH, 1);
            zeroTime(cal);

            selectedMonthDate = cal;
            renderChartByCurrentPeriod();
        });
    }
    private void zeroTime(Calendar cal) {
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
    }

    private Calendar getLatestDateFromData() {
        if (dayDocs.isEmpty()) return Calendar.getInstance();

        String latestKey = null;
        for (String key : dayDocs.keySet()) {
            latestKey = key;
        }

        Calendar cal = Calendar.getInstance();
        try {
            cal.setTime(sdf.parse(latestKey));
        } catch (Exception ignored) {}

        return cal;
    }

}
