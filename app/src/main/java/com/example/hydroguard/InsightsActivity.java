package com.example.hydroguard;

import android.graphics.Color;
import android.os.Bundle;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

public class InsightsActivity extends BaseDrawerActivity {

    private TextView tvMonth, tvAvgDaily, tvHighestDay, tvTotalMonth, tvProjectedMonth, tvEstimatedBill, tvTips;

    private BarChart chartLast7Days;
    private BarChart chartHourlyToday;
    private LineChart chartWeekly;

    private RecyclerView rvTopCategories;
    private final ArrayList<CategoryUsageRow> topCategoryRows = new ArrayList<>();
    private CategoryUsageAdapter topCategoryAdapter;

    private final HashMap<String, String> categoryIdToName = new HashMap<>();

    private String savedDailyTip = null;

    private ValueEventListener historyListener;

    private float monthlyGoalLiters = 0f;
    private float monthlyLimitLiters = 0f;
    private float dailyGoalLiters = 0f;
    private float dailyLimitLiters = 0f;

    private final HashMap<String, Float> todayHourlyCache = new HashMap<>();

    private float calculateAirSelangorBill(float liters) {
        float m3 = liters / 1000f;
        float cost;

        if (m3 <= 20) {
            cost = m3 * 0.57f;
        } else if (m3 <= 35) {
            cost = (20 * 0.57f) + ((m3 - 20) * 1.03f);
        } else {
            cost = (20 * 0.57f) + (15 * 1.03f) + ((m3 - 35) * 2.00f);
        }
        return cost;
    }

    @Override
    protected int getLayoutResId() {
        return R.layout.activity_insights;
    }

    @Override
    protected int getCurrentNavItemId() {
        return R.id.nav_insights;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        tvTips = findViewById(R.id.tvTips);
        tvMonth = findViewById(R.id.tvMonth);
        tvAvgDaily = findViewById(R.id.tvAvgDaily);
        tvHighestDay = findViewById(R.id.tvHighestDay);
        tvTotalMonth = findViewById(R.id.tvTotalMonth);
        tvProjectedMonth = findViewById(R.id.tvProjectedMonth);
        tvEstimatedBill = findViewById(R.id.tvEstimatedBill);

        chartLast7Days = findViewById(R.id.chartLast7Days);
        chartWeekly = findViewById(R.id.chartWeekly);
        chartHourlyToday = findViewById(R.id.chartHourlyToday);

        rvTopCategories = findViewById(R.id.rvTopCategories);
        rvTopCategories.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        topCategoryAdapter = new CategoryUsageAdapter(topCategoryRows);
        rvTopCategories.setAdapter(topCategoryAdapter);
        rvTopCategories.setNestedScrollingEnabled(false);

        setupCharts();

        initCoreCategoryMapping();

        loadAll();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        detachHistoryListener();
    }

    private void detachHistoryListener() {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) return;

        if (historyListener != null) {
            FirebaseDatabase.getInstance()
                    .getReference("hydroguard/historyDaily/" + uid)
                    .removeEventListener(historyListener);
        }
        historyListener = null;
    }

    private void initCoreCategoryMapping() {
        categoryIdToName.clear();
        categoryIdToName.put("tap", "Tap");
        categoryIdToName.put("shower", "Shower");
        categoryIdToName.put("uncategorized", "Uncategorized");
    }

    private void setupCharts() {
        // Last 7 days
        chartLast7Days.getDescription().setEnabled(false);
        chartLast7Days.setTouchEnabled(true);
        chartLast7Days.setPinchZoom(true);
        chartLast7Days.getAxisRight().setEnabled(false);
        chartLast7Days.setDrawGridBackground(false);
        chartLast7Days.setBackgroundColor(Color.TRANSPARENT);

        XAxis bx = chartLast7Days.getXAxis();
        bx.setPosition(XAxis.XAxisPosition.BOTTOM);
        bx.setGranularity(1f);
        bx.setTextSize(10f);
        bx.setLabelRotationAngle(-35f);

        chartWeekly.getDescription().setEnabled(false);
        chartWeekly.setTouchEnabled(true);
        chartWeekly.setPinchZoom(true);
        chartWeekly.getAxisRight().setEnabled(false);

        XAxis lx = chartWeekly.getXAxis();
        lx.setPosition(XAxis.XAxisPosition.BOTTOM);
        lx.setGranularity(1f);
        lx.setTextSize(10f);
        lx.setLabelRotationAngle(-25f);

        chartHourlyToday.getDescription().setEnabled(false);
        chartHourlyToday.setTouchEnabled(true);
        chartHourlyToday.setPinchZoom(true);
        chartHourlyToday.getAxisRight().setEnabled(false);
        chartHourlyToday.setDrawGridBackground(false);
        chartHourlyToday.setBackgroundColor(Color.TRANSPARENT);

        XAxis hx = chartHourlyToday.getXAxis();
        hx.setPosition(XAxis.XAxisPosition.BOTTOM);
        hx.setGranularity(1f);
        hx.setTextSize(10f);
        hx.setLabelRotationAngle(-35f);
    }

    private void loadAll() {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) {
            showEmptyState();
            return;
        }

        FirebaseDatabase.getInstance()
                .getReference("hydroguard/settings/" + uid)
                .get()
                .addOnSuccessListener(s -> {
                    monthlyGoalLiters = readFloatFlexible(s.child("monthlyGoalLiters").getValue());
                    monthlyLimitLiters = readFloatFlexible(s.child("monthlyLimitLiters").getValue());
                    dailyGoalLiters = readFloatFlexible(s.child("dailyGoalLiters").getValue());
                    dailyLimitLiters = readFloatFlexible(s.child("dailyLimitLiters").getValue());

                    loadCategoriesThenTip(uid);
                })
                .addOnFailureListener(e -> loadCategoriesThenTip(uid));
    }

    private void loadCategoriesThenTip(String uid) {
        FirebaseDatabase.getInstance()
                .getReference("hydroguard/categories/" + uid)
                .get()
                .addOnSuccessListener(snapshot -> {
                    for (DataSnapshot s : snapshot.getChildren()) {
                        String id = s.child("id").getValue(String.class);
                        String name = s.child("name").getValue(String.class);
                        if (id == null) id = s.getKey();

                        if (id != null && name != null && !categoryIdToName.containsKey(id)) {
                            categoryIdToName.put(id, name);
                        }
                    }
                    loadTodaySavedTipThenAttach(uid);
                })
                .addOnFailureListener(e -> loadTodaySavedTipThenAttach(uid));
    }

    private void loadTodaySavedTipThenAttach(String uid) {
        String dateKey = dateKeyNow();

        FirebaseDatabase.getInstance()
                .getReference("hydroguard/insightsDaily/" + uid + "/" + dateKey)
                .get()
                .addOnSuccessListener(snapshot -> {
                    savedDailyTip = snapshot.exists() ? snapshot.child("tip").getValue(String.class) : null;
                    attachHistoryDailyListener(uid);
                })
                .addOnFailureListener(e -> {
                    savedDailyTip = null;
                    attachHistoryDailyListener(uid);
                });
    }

    private void attachHistoryDailyListener(String uid) {
        if (uid == null) return;

        if (historyListener != null) return;

        historyListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {

                TreeMap<String, Float> dailyTotals = new TreeMap<>();
                HashMap<String, Float> monthCategorySum = new HashMap<>();
                HashMap<String, Float> todayHourly = new HashMap<>();

                Calendar now = Calendar.getInstance();
                String todayKey = dateKeyNow();
                String monthKey =
                        new SimpleDateFormat("yyyy-MM", Locale.getDefault())
                                .format(now.getTime());

                for (DataSnapshot daySnap : snapshot.getChildren()) {
                    String dateKey = daySnap.getKey();
                    if (dateKey == null) continue;

                    float total =
                            readFloatFlexible(daySnap.child("totalLiters").getValue());
                    dailyTotals.put(dateKey, total);

                    if (dateKey.startsWith(monthKey)) {
                        DataSnapshot cats = daySnap.child("categories");
                        if (cats.exists()) {
                            for (DataSnapshot c : cats.getChildren()) {
                                String catId = c.getKey();
                                if (catId == null) continue;

                                float liters = readFloatFlexible(c.getValue());
                                monthCategorySum.put(
                                        catId,
                                        monthCategorySum.getOrDefault(catId, 0f) + liters
                                );
                            }
                        }
                    }

                    if (dateKey.equals(todayKey)) {
                        DataSnapshot hours = daySnap.child("hourly");
                        if (hours.exists()) {
                            for (DataSnapshot h : hours.getChildren()) {
                                String hour = h.getKey();
                                if (hour == null) continue;

                                float liters = readFloatFlexible(h.getValue());
                                todayHourly.put(normalizeHour(hour), liters);
                            }
                        }
                    }
                }

                buildInsights(dailyTotals, monthCategorySum, todayHourly);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                showEmptyState();
            }
        };

        FirebaseDatabase.getInstance()
                .getReference("hydroguard/historyDaily/" + uid)
                .limitToLast(90) // ✅ performance guard
                .addValueEventListener(historyListener);
    }

    private void buildInsights(TreeMap<String, Float> dailyTotals,
                               HashMap<String, Float> monthCategorySum,
                               HashMap<String, Float> todayHourly) {

        if (dailyTotals == null || dailyTotals.isEmpty()) {
            showEmptyState();
            return;
        }

        Calendar now = Calendar.getInstance();
        String monthKey =
                new SimpleDateFormat("yyyy-MM", Locale.getDefault())
                        .format(now.getTime());
        String monthLabel =
                new SimpleDateFormat("MMMM yyyy", Locale.getDefault())
                        .format(now.getTime());

        if (tvMonth != null) tvMonth.setText(monthLabel);

        float monthTotal = 0f;
        float highestVal = -1f;
        String highestDay = "-";
        int daysCount = 0;

        for (Map.Entry<String, Float> e : dailyTotals.entrySet()) {
            String date = e.getKey();
            float liters = e.getValue();

            if (date != null && date.startsWith(monthKey)) {
                monthTotal += liters;
                daysCount++;

                if (liters > highestVal) {
                    highestVal = liters;
                    highestDay = date;
                }
            }
        }

        float avgDaily = daysCount > 0 ? (monthTotal / daysCount) : 0f;

        if (tvTotalMonth != null)
            tvTotalMonth.setText(
                    String.format(Locale.getDefault(),
                            "Total this month: %.1f L", monthTotal));

        if (tvAvgDaily != null)
            tvAvgDaily.setText(
                    String.format(Locale.getDefault(),
                            "Avg/day: %.1f L/day", avgDaily));

        if (tvHighestDay != null) {
            tvHighestDay.setText(
                    highestVal >= 0f
                            ? String.format(Locale.getDefault(),
                            "Highest day: %s (%.1f L)", highestDay, highestVal)
                            : "Highest day: -"
            );
        }

        int daysInMonth = now.getActualMaximum(Calendar.DAY_OF_MONTH);
        float projected = avgDaily * daysInMonth;

        if (tvProjectedMonth != null)
            tvProjectedMonth.setText(
                    String.format(Locale.getDefault(),
                            "Projected: %.1f L", projected));

        float estBill = calculateAirSelangorBill(projected);
        if (tvEstimatedBill != null)
            tvEstimatedBill.setText(
                    String.format(Locale.getDefault(),
                            "Estimated bill: RM %.2f", estBill));

        topCategoryRows.clear();
        if (monthCategorySum != null) {
            for (Map.Entry<String, Float> e : monthCategorySum.entrySet()) {
                if (e.getValue() <= 0f) continue;

                String catName =
                        categoryIdToName.getOrDefault(e.getKey(), e.getKey());

                topCategoryRows.add(
                        new CategoryUsageRow(catName, e.getValue()));
            }
        }

        topCategoryRows.sort((a, b) -> Float.compare(b.liters, a.liters));
        if (topCategoryRows.size() > 6)
            topCategoryRows.subList(6, topCategoryRows.size()).clear();

        topCategoryAdapter.submitList(new ArrayList<>(topCategoryRows));

        buildLast7DaysChart(dailyTotals);
        buildWeeklyChart(dailyTotals);
        buildHourlyTodayChart(todayHourly);

        if (tvTips != null) {
            if (savedDailyTip != null && !savedDailyTip.trim().isEmpty()) {
                tvTips.setText("• " + savedDailyTip.trim());
            } else {
                buildTips(avgDaily, highestVal, highestDay,
                        projected, estBill,
                        getPeakHour(todayHourly),
                        getPeakHourValue(todayHourly),
                        topCategoryRows,
                        sumToday(todayHourly));
            }
        }
    }

    private void buildLast7DaysChart(TreeMap<String, Float> dailyTotals) {

        ArrayList<String> labels = new ArrayList<>();
        ArrayList<BarEntry> entries = new ArrayList<>();

        ArrayList<String> keys = new ArrayList<>(dailyTotals.keySet());
        int start = Math.max(0, keys.size() - 7);

        int index = 0;
        for (int i = start; i < keys.size(); i++) {
            String date = keys.get(i);
            float liters = dailyTotals.getOrDefault(date, 0f);

            entries.add(new BarEntry(index, liters));
            labels.add(date.length() >= 10 ? date.substring(5) : date);
            index++;
        }

        showLast7DaysChart(entries, labels);
    }

    private void buildWeeklyChart(TreeMap<String, Float> dailyTotals) {

        TreeMap<String, Float> weekTotals = new TreeMap<>();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());

        for (Map.Entry<String, Float> e : dailyTotals.entrySet()) {
            try {
                Calendar c = Calendar.getInstance();
                c.setTime(sdf.parse(e.getKey()));

                int year = c.get(Calendar.YEAR);
                int week = c.get(Calendar.WEEK_OF_YEAR);
                String key = year + "-W" + String.format(Locale.getDefault(), "%02d", week);

                weekTotals.put(key, weekTotals.getOrDefault(key, 0f) + e.getValue());

            } catch (Exception ignored) {}
        }

        ArrayList<Entry> entries = new ArrayList<>();
        ArrayList<String> labels = new ArrayList<>();

        ArrayList<String> keys = new ArrayList<>(weekTotals.keySet());
        int start = Math.max(0, keys.size() - 8);

        int index = 0;
        for (int i = start; i < keys.size(); i++) {
            String wk = keys.get(i);
            entries.add(new Entry(index, weekTotals.get(wk)));
            labels.add(wk.substring(wk.indexOf("-W") + 1));
            index++;
        }

        showWeeklyChart(entries, labels);
    }
    private void buildHourlyTodayChart(HashMap<String, Float> todayHourly) {

        ArrayList<BarEntry> entries = new ArrayList<>();
        ArrayList<String> labels = new ArrayList<>();

        todayHourlyCache.clear();
        todayHourlyCache.putAll(todayHourly);

        for (int h = 0; h < 24; h++) {
            String hh = String.format(Locale.getDefault(), "%02d", h);
            float liters = todayHourly.getOrDefault(hh, 0f);

            entries.add(new BarEntry(h, liters));
            labels.add(hh);
        }

        showHourlyTodayChart(entries, labels);
    }

    private String getPeakHour(HashMap<String, Float> todayHourly) {

        float max = 0f;
        String peak = "00";

        for (Map.Entry<String, Float> e : todayHourly.entrySet()) {
            if (e.getValue() > max) {
                max = e.getValue();
                peak = e.getKey();
            }
        }
        return peak;
    }

    private float getPeakHourValue(HashMap<String, Float> todayHourly) {

        float max = 0f;
        for (float v : todayHourly.values()) {
            if (v > max) max = v;
        }
        return max;
    }

    private float sumToday(HashMap<String, Float> todayHourly) {

        float sum = 0f;
        for (float v : todayHourly.values()) {
            sum += v;
        }
        return sum;
    }

    private void buildTips(float avgDaily,
                           float highestVal,
                           String highestDay,
                           float projected,
                           float estBill,
                           String peakHour,
                           float peakHourVal,
                           ArrayList<CategoryUsageRow> topCats,
                           float todayTotalFromHourly) {

        StringBuilder tips = new StringBuilder();

        if (monthlyLimitLiters > 0f && projected > monthlyLimitLiters) {
            tips.append("• Warning: Projected usage exceeds your monthly limit (")
                    .append(String.format(Locale.getDefault(), "%.0f L", monthlyLimitLiters))
                    .append("). Reduce usage to avoid high bill.\n");
        } else if (monthlyGoalLiters > 0f && projected > monthlyGoalLiters) {
            tips.append("• Projected usage is above your monthly goal (")
                    .append(String.format(Locale.getDefault(), "%.0f L", monthlyGoalLiters))
                    .append("). Try saving water this week.\n");
        } else if (monthlyGoalLiters > 0f && projected <= monthlyGoalLiters) {
            tips.append("• Good: You are on track to meet your monthly goal.\n");
        }

        if (dailyLimitLiters > 0f && avgDaily > dailyLimitLiters) {
            tips.append("• Daily limit exceeded (")
                    .append(String.format(Locale.getDefault(), "%.0f L/day", dailyLimitLiters))
                    .append("). Reduce usage today.\n");
        } else if (dailyGoalLiters > 0f && avgDaily > dailyGoalLiters) {
            tips.append("• Above daily goal (")
                    .append(String.format(Locale.getDefault(), "%.0f L/day", dailyGoalLiters))
                    .append("). Try reducing usage for the next few days.\n");
        }

        if (monthlyGoalLiters <= 0f && monthlyLimitLiters <= 0f && dailyGoalLiters <= 0f && dailyLimitLiters <= 0f) {
            if (avgDaily <= 0f) {
                tips.append("• No usage recorded yet. Start tracking to generate insights.\n");
            } else if (avgDaily > 250f) {
                tips.append("• High daily usage. Reduce shower time and avoid leaving taps running.\n");
            } else if (avgDaily > 150f) {
                tips.append("• Moderate daily usage. Consider a water-saving shower head.\n");
            } else {
                tips.append("• Good daily usage. Keep maintaining these habits.\n");
            }
        }

        if (highestVal >= 400f && highestDay != null && !highestDay.equals("-")) {
            tips.append("• Very high usage on ").append(highestDay).append(". Check for leaks or unusual activity.\n");
        }

        float avgHourToday = todayTotalFromHourly / 24f;
        if (avgHourToday > 0f && peakHourVal > (avgHourToday * 5f)) {
            tips.append("• Spike detected at ").append(peakHour).append(":00 (")
                    .append(String.format(Locale.getDefault(), "%.1f L", peakHourVal))
                    .append("). Try spreading heavy usage to avoid peaks.\n");
        } else if (peakHourVal > 0f) {
            tips.append("• Peak hour today: ").append(peakHour).append(":00 (")
                    .append(String.format(Locale.getDefault(), "%.1f L", peakHourVal))
                    .append(").\n");
        }

        int activeHours = countActiveHoursToday(0.5f);
        boolean nightUsage = hasNightUsageToday(0.5f);
        if (activeHours >= 12 || nightUsage) {
            tips.append("• Possible leak/continuous flow detected (usage across many hours). Check taps, toilet tank, or pipe leaks.\n");
        }

        if (topCats != null && !topCats.isEmpty()) {
            CategoryUsageRow top = topCats.get(0);

            tips.append("• Top category this month: ").append(top.name)
                    .append(String.format(Locale.getDefault(), " (%.1f L). ", top.liters));

            String n = top.name.toLowerCase(Locale.getDefault());
            if (n.contains("shower")) tips.append("Shorten showers by 2–3 minutes.\n");
            else if (n.contains("tap")) tips.append("Avoid running taps while washing dishes/teeth.\n");
            else tips.append("Focus on reducing usage in this category.\n");
        }

        tips.append("• Estimated bill (projection): RM ")
                .append(String.format(Locale.getDefault(), "%.2f", estBill))
                .append(".");

        tvTips.setText(tips.toString().trim());
    }

    private void showLast7DaysChart(ArrayList<BarEntry> entries, ArrayList<String> labels) {

        if (entries.isEmpty()) {
            chartLast7Days.clear();
            chartLast7Days.invalidate();
            return;
        }

        BarDataSet dataSet = new BarDataSet(entries, "Last 7 Days (L)");
        dataSet.setDrawValues(true);
        dataSet.setValueTextSize(10f);
        dataSet.setColor(getColor(R.color.hg_accentTeal));

        BarData data = new BarData(dataSet);
        data.setBarWidth(0.45f); // 🔑 thinner bars → more spacing

        chartLast7Days.setData(data);
        chartLast7Days.setFitBars(true);

        // X axis
        XAxis x = chartLast7Days.getXAxis();
        x.setValueFormatter(new IndexAxisValueFormatter(labels));
        x.setGranularity(1f);
        x.setLabelCount(labels.size(), false);
        x.setPosition(XAxis.XAxisPosition.BOTTOM);
        x.setDrawGridLines(false);
        x.setLabelRotationAngle(-30f);
        x.setAvoidFirstLastClipping(true);

        // Y axis
        chartLast7Days.getAxisLeft().setAxisMinimum(0f);
        chartLast7Days.getAxisLeft().setDrawGridLines(true);

        chartLast7Days.getAxisRight().setEnabled(false);
        chartLast7Days.getLegend().setEnabled(false);

        chartLast7Days.setExtraBottomOffset(12f);
        chartLast7Days.animateY(600);

        chartLast7Days.invalidate();
    }

    private void showWeeklyChart(ArrayList<Entry> entries, ArrayList<String> labels) {
        if (entries == null || entries.isEmpty()) {
            chartWeekly.clear();
            chartWeekly.invalidate();
            return;
        }

        LineDataSet ds = new LineDataSet(entries, "Weekly Total (L)");
        ds.setLineWidth(3f);
        ds.setCircleRadius(4f);
        ds.setDrawValues(false);
        ds.setMode(LineDataSet.Mode.LINEAR);
        ds.setColor(getColor(R.color.hg_accentTeal));
        ds.setCircleColor(getColor(R.color.hg_accentTeal));

        chartWeekly.setData(new LineData(ds));

        XAxis x = chartWeekly.getXAxis();
        x.setValueFormatter(new IndexAxisValueFormatter(labels));
        x.setPosition(XAxis.XAxisPosition.BOTTOM);
        x.setDrawGridLines(false);
        x.setGranularity(1f);
        x.setLabelCount(Math.min(labels.size(), 5), true);
        x.setAvoidFirstLastClipping(true);
        x.setTextSize(9f);

        chartWeekly.getAxisLeft().setAxisMinimum(0f);
        chartWeekly.getAxisLeft().setDrawGridLines(true);
        chartWeekly.getAxisRight().setEnabled(false);
        chartWeekly.getLegend().setEnabled(false);

        // 🔑 CRITICAL VISUAL FIXES
        chartWeekly.setExtraTopOffset(16f);
        chartWeekly.setExtraBottomOffset(12f);
        chartWeekly.setClipChildren(false);
        chartWeekly.setClipToPadding(false);

        chartWeekly.animateX(600);
        chartWeekly.invalidate();
    }

    private void showHourlyTodayChart(ArrayList<BarEntry> entries, ArrayList<String> labels) {

        boolean hasData = false;
        for (BarEntry e : entries) {
            if (e.getY() > 0f) {
                hasData = true;
                break;
            }
        }

        if (!hasData) {
            chartHourlyToday.clear();
            chartHourlyToday.setNoDataText("No water usage recorded today");
            chartHourlyToday.invalidate();
            return;
        }

        BarDataSet dataSet = new BarDataSet(entries, "Hourly Usage (L)");
        dataSet.setColor(getColor(R.color.hg_secondary));
        dataSet.setDrawValues(true);

        BarData data = new BarData(dataSet);
        data.setBarWidth(0.6f);

        chartHourlyToday.setData(data);

        XAxis x = chartHourlyToday.getXAxis();
        x.setValueFormatter(new IndexAxisValueFormatter(labels));
        x.setGranularity(1f);
        x.setLabelCount(6, true);
        x.setAvoidFirstLastClipping(true);
        x.setPosition(XAxis.XAxisPosition.BOTTOM);
        x.setDrawGridLines(false);

        chartHourlyToday.getAxisLeft().setAxisMinimum(0f);
        chartHourlyToday.getAxisRight().setEnabled(false);
        chartHourlyToday.getLegend().setEnabled(false);

        chartHourlyToday.animateY(500);
        chartHourlyToday.invalidate();
    }

    private void showEmptyState() {

        if (tvMonth != null) tvMonth.setText("No data");
        if (tvAvgDaily != null) tvAvgDaily.setText("Avg/day: 0.0 L/day");
        if (tvHighestDay != null) tvHighestDay.setText("Highest day: -");
        if (tvTotalMonth != null) tvTotalMonth.setText("Total this month: 0.0 L");
        if (tvProjectedMonth != null) tvProjectedMonth.setText("Projected: 0.0 L");
        if (tvEstimatedBill != null)
            tvEstimatedBill.setText("Estimated bill: RM 0.00");

        if (tvTips != null) {
            tvTips.setText(
                    "• No usage data found yet.\n• Start tracking to see insights and tips.");
        }

        topCategoryRows.clear();
        topCategoryAdapter.submitList(new ArrayList<>());

        todayHourlyCache.clear();

        chartLast7Days.clear();
        chartWeekly.clear();
        chartHourlyToday.clear();

        chartLast7Days.invalidate();
        chartWeekly.invalidate();
        chartHourlyToday.invalidate();
    }

    private float readFloatFlexible(Object v) {
        try {
            if (v instanceof Number)
                return ((Number) v).floatValue();

            if (v instanceof String)
                return Float.parseFloat(((String) v).trim());
        } catch (Exception ignored) { }
        return 0f;
    }


    private String dateKeyNow() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                .format(Calendar.getInstance().getTime());
    }

    private String normalizeHour(String hour) {
        if (hour == null) return "00";

        try {
            int h = Integer.parseInt(hour.trim());
            h = Math.max(0, Math.min(23, h));
            return String.format(Locale.getDefault(), "%02d", h);
        } catch (Exception ignored) {
            return "00";
        }
    }

    private int countActiveHoursToday(float thresholdLiters) {
        int count = 0;
        for (int h = 0; h < 24; h++) {
            String hh = String.format(Locale.getDefault(), "%02d", h);
            float v = todayHourlyCache.containsKey(hh) ? todayHourlyCache.get(hh) : 0f;
            if (v >= thresholdLiters) count++;
        }
        return count;
    }

    private boolean hasNightUsageToday(float thresholdLiters) {
        for (int h = 0; h <= 5; h++) {
            String hh = String.format(Locale.getDefault(), "%02d", h);
            float v = todayHourlyCache.containsKey(hh) ? todayHourlyCache.get(hh) : 0f;
            if (v >= thresholdLiters) return true;
        }
        return false;
    }
}
