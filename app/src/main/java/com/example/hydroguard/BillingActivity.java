package com.example.hydroguard;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;

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
import java.util.Locale;

public class BillingActivity extends BaseDrawerActivity {

    private TextView monthTitleText, currentUsageText, lastMonthSummaryText;
    private Chip currentCostText, paymentStatusChip;
    private BarChart billingChart;
    private MaterialButton btnPayNow;

    private RecyclerView billingRecycler;
    private final ArrayList<BillingDayModel> dayList = new ArrayList<>();
    private BillingDayAdapter dayAdapter;

    private RecyclerView paymentRecycler;
    private final ArrayList<PaymentModel> paymentList = new ArrayList<>();
    private PaymentAdapter paymentAdapter;

    private String uid;
    private float latestCurrentMonthCost = 0f;
    private float latestCurrentMonthTotalLiters = 0f;

    private DatabaseReference historyDailyRef;
    private DatabaseReference paymentsRef;
    private ValueEventListener paymentsListener;

    @Override
    protected int getLayoutResId() {
        return R.layout.activity_billing;
    }

    @Override
    protected int getCurrentNavItemId() {
        return R.id.nav_billing;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) {
            finish();
            return;
        }

        bindViews();
        setupRecycler();
        setupChartAppearance();

        historyDailyRef = FirebaseDatabase.getInstance()
                .getReference("hydroguard")
                .child("historyDaily")
                .child(uid);

        loadBillingFromHistoryDaily();

        if (btnPayNow != null) {
            btnPayNow.setOnClickListener(v -> openPaymentPage());
        }

    }

    @Override
    protected void onStop() {
        super.onStop();
        detachPaymentsListener();
    }

    private void bindViews() {
        monthTitleText = findViewById(R.id.monthTitleText);
        currentUsageText = findViewById(R.id.currentUsageText);
        currentCostText = findViewById(R.id.currentCostText);
        paymentStatusChip = findViewById(R.id.paymentStatusChip);
        lastMonthSummaryText = findViewById(R.id.lastMonthSummaryText);
        billingChart = findViewById(R.id.billingChart);
        btnPayNow = findViewById(R.id.btnPayNow);

        billingRecycler = findViewById(R.id.billingRecycler);
        paymentRecycler = findViewById(R.id.paymentRecycler);
    }

    private void setupRecycler() {
        billingRecycler.setLayoutManager(new LinearLayoutManager(this));
        dayAdapter = new BillingDayAdapter(dayList);
        billingRecycler.setAdapter(dayAdapter);
        billingRecycler.setNestedScrollingEnabled(false);

        paymentRecycler.setLayoutManager(new LinearLayoutManager(this));
        paymentAdapter = new PaymentAdapter(paymentList);
        paymentRecycler.setAdapter(paymentAdapter);
        paymentRecycler.setNestedScrollingEnabled(false);
    }

    private void setupChartAppearance() {
        billingChart.getDescription().setEnabled(false);
        billingChart.getAxisRight().setEnabled(false);
        billingChart.setTouchEnabled(true);
        billingChart.setPinchZoom(true);

        XAxis xAxis = billingChart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setGranularity(1f);
    }

    private void openPaymentPage() {
        Intent i = new Intent(this, PaymentActivity.class);
        i.putExtra("monthLabel", monthTitleText.getText().toString());
        i.putExtra("usageText", currentUsageText.getText().toString());
        i.putExtra("amount", latestCurrentMonthCost);
        startActivity(i);
    }

    private void loadBillingFromHistoryDaily() {

        historyDailyRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {

                dayList.clear();

                Calendar now = Calendar.getInstance();
                SimpleDateFormat monthKeyFmt = new SimpleDateFormat("yyyy-MM", Locale.getDefault());
                SimpleDateFormat monthLabelFmt = new SimpleDateFormat("MMMM yyyy", Locale.getDefault());

                String cmKey = monthKeyFmt.format(now.getTime());
                String cmLabel = monthLabelFmt.format(now.getTime());

                Calendar prev = (Calendar) now.clone();
                prev.add(Calendar.MONTH, -1);
                String lmKey = monthKeyFmt.format(prev.getTime());
                String lmLabel = monthLabelFmt.format(prev.getTime());

                float cmTap = 0f, cmShower = 0f, cmUncat = 0f, cmTotal = 0f;
                float lmTap = 0f, lmShower = 0f, lmUncat = 0f, lmTotal = 0f;

                for (DataSnapshot daySnap : snapshot.getChildren()) {
                    String dateKey = daySnap.getKey();
                    if (TextUtils.isEmpty(dateKey)) continue;

                    float total = readFloatFlexible(daySnap.child("totalLiters").getValue());
                    float tap = readFloatFlexible(daySnap.child("categories").child("tap").getValue());
                    float shower = readFloatFlexible(daySnap.child("categories").child("shower").getValue());
                    float uncat = readFloatFlexible(daySnap.child("categories").child("uncategorized").getValue());

                    float known = tap + shower + uncat;
                    if (total > known) uncat += (total - known);
                    if (total <= 0f) total = known;

                    if (dateKey.startsWith(cmKey)) {
                        cmTap += tap;
                        cmShower += shower;
                        cmUncat += uncat;
                        cmTotal += total;

                        dayList.add(new BillingDayModel(
                                dateKey, tap, shower, uncat, total, 0f, "-"
                        ));
                    }

                    if (dateKey.startsWith(lmKey)) {
                        lmTap += tap;
                        lmShower += shower;
                        lmUncat += uncat;
                        lmTotal += total;
                    }
                }

                Collections.sort(dayList, (a, b) -> a.date.compareTo(b.date));

                float currentCost = calculateAirSelangorBill(cmTotal);
                float lastCost = calculateAirSelangorBill(lmTotal);

                latestCurrentMonthCost = currentCost;
                latestCurrentMonthTotalLiters = cmTotal;

                float cumulative = 0f;
                for (BillingDayModel d : dayList) {
                    cumulative += d.totalLiters;
                    d.cost = (cmTotal > 0f)
                            ? (d.totalLiters / cmTotal) * currentCost
                            : 0f;
                    d.blockInfo = getBlockTier(cumulative);
                }

                dayAdapter.notifyDataSetChanged();

                monthTitleText.setText(cmLabel);
                currentUsageText.setText(buildTwoLineUsageText(cmTap, cmShower, cmUncat));
                currentCostText.setText(String.format(Locale.getDefault(), "RM %.2f", currentCost));
                lastMonthSummaryText.setText(
                        buildLastMonthSummary(lmLabel, lmTap, lmShower, lmUncat, lmTotal, lastCost)
                );

                showComparisonChart(cmTotal, lmTotal, cmLabel, lmLabel);
                setPaidStatus(false);
                loadPaymentHistory();
            }

            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void loadPaymentHistory() {

        detachPaymentsListener();

        paymentsRef = FirebaseDatabase.getInstance()
                .getReference("hydroguard")
                .child("payments")
                .child(uid);

        paymentsListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {

                paymentList.clear();
                boolean paidThisMonth = false;

                String currentMonth = monthTitleText != null
                        ? monthTitleText.getText().toString()
                        : "";

                for (DataSnapshot s : snapshot.getChildren()) {
                    PaymentModel p = s.getValue(PaymentModel.class);
                    if (p == null) continue;

                    paymentList.add(p);

                    if (currentMonth.equalsIgnoreCase(p.month)
                            && p.status != null
                            && p.status.contains("SUCCESS")) {
                        paidThisMonth = true;
                    }
                }

                Collections.sort(paymentList,
                        (a, b) -> Long.compare(b.getReceiptTime(), a.getReceiptTime()));

                paymentAdapter.notifyDataSetChanged();
                setPaidStatus(paidThisMonth);
            }

            @Override public void onCancelled(@NonNull DatabaseError error) {}
        };

        paymentsRef.addValueEventListener(paymentsListener);
    }

    private void detachPaymentsListener() {
        if (paymentsRef != null && paymentsListener != null) {
            paymentsRef.removeEventListener(paymentsListener);
        }
        paymentsListener = null;
        paymentsRef = null;
    }

    private void setPaidStatus(boolean paid) {
        if (paid) {
            paymentStatusChip.setText("PAID ✅");
            btnPayNow.setEnabled(false);
            btnPayNow.setAlpha(0.5f);
        } else {
            paymentStatusChip.setText("UNPAID ⏳");
            btnPayNow.setEnabled(latestCurrentMonthCost > 0f);
            btnPayNow.setAlpha(latestCurrentMonthCost > 0f ? 1f : 0.5f);
        }
    }

    private float readFloatFlexible(Object v) {
        try {
            if (v instanceof Number) return ((Number) v).floatValue();
            if (v instanceof String) return Float.parseFloat(((String) v).trim());
        } catch (Exception ignored) {}
        return 0f;
    }

    private float calculateAirSelangorBill(float liters) {
        float m3 = liters / 1000f;
        if (m3 <= 20f) return m3 * 0.57f;
        if (m3 <= 35f) return (20f * 0.57f) + ((m3 - 20f) * 1.03f);
        return (20f * 0.57f) + (15f * 1.03f) + ((m3 - 35f) * 2.00f);
    }

    private String getBlockTier(float liters) {
        float m3 = liters / 1000f;
        if (m3 <= 20f) return "Block 1 (RM0.57)";
        if (m3 <= 35f) return "Block 2 (RM1.03)";
        return "Block 3 (RM2.00)";
    }

    private String buildTwoLineUsageText(float tap, float shower, float uncat) {
        String s = String.format(Locale.getDefault(),
                "Tap: %.1f L\nShower: %.1f L", tap, shower);
        if (uncat > 0.5f) {
            s += String.format(Locale.getDefault(), "\nUncategorized: %.1f L", uncat);
        }
        return s;
    }

    private String buildLastMonthSummary(String label, float tap, float shower,
                                         float uncat, float total, float cost) {
        String s = label + "\n" +
                String.format(Locale.getDefault(),
                        "Tap: %.1f L | Shower: %.1f L", tap, shower);
        if (uncat > 0.5f) {
            s += String.format(Locale.getDefault(), " | Uncat: %.1f L", uncat);
        }
        s += String.format(Locale.getDefault(),
                "\nTotal: %.1f L (RM %.2f)", total, cost);
        return s;
    }

    private void showComparisonChart(float current, float last,
                                     String cmLabel, String lmLabel) {

        if (billingChart == null) return;

        ArrayList<BarEntry> entries = new ArrayList<>();
        entries.add(new BarEntry(0, last));
        entries.add(new BarEntry(1, current));

        BarDataSet set = new BarDataSet(entries, "Monthly Usage (Liters)");
        BarData data = new BarData(set);
        data.setBarWidth(0.5f);

        billingChart.setData(data);
        billingChart.getXAxis().setValueFormatter(
                new IndexAxisValueFormatter(new String[]{lmLabel, cmLabel})
        );
        billingChart.invalidate();
    }
}
