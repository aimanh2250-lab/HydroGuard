package com.example.hydroguard;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

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
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class DashboardActivity extends BaseDrawerActivity {

    private String activeDeviceId;
    private String uid;

    private static final String LINE_SHOWER = "L1"; // Shower
    private static final String LINE_TAP    = "L2"; // Tap

    private static final float DAILY_LIMIT_MULT = 2.0f;
    private static final float MONTHLY_LIMIT_MULT = 3.0f;

    private static final long OFFLINE_GRACE_MS = 8000;
    private long dashboardStartMs = 0;

    private static final long OFFLINE_TIMEOUT_MS = 30_000; // 30s (adjust)
    private boolean isDeviceOnline = false;
    private ValueEventListener lastSeenListener;
    private DatabaseReference lastSeenRef;

    private boolean alertStatePrimed = false;

    private ImageView ivAnimatedBg;

    private TextView flowValue, usageValue;
    private Chip chipStatus;

    private TextView tvLineAFlow;
    private Chip chipLineAValve, chipLineALeak;
    private MaterialButton btnLineAToggle;

    private TextView tvLineBFlow;
    private Chip chipLineBValve, chipLineBLeak;
    private MaterialButton btnLineBToggle;

    private ProgressBar dailyProgress, monthlyProgress;
    private TextView dailyProgressText, monthlyProgressText;

    private RecyclerView sensorsRecycler;
    private final ArrayList<SensorModel> sensorList = new ArrayList<>();
    private SensorsAdapter sensorAdapter;

    private RecyclerView categoryUsageRecycler;
    private final ArrayList<CategoryUsageRow> categoryUsageRows = new ArrayList<>();
    private CategoryUsageAdapter categoryUsageAdapter;

    private ObjectAnimator bgPanX, bgPanY, bgScaleX, bgScaleY;

    private Float todayUsageLiters = null;
    private Float monthUsageLiters = null;
    private Float dailyGoalLiters = null;
    private Float monthlyGoalLiters = null;

    private ValueEventListener deviceLinesListener;
    private ValueEventListener todayHistoryListener;
    private ValueEventListener monthHistoryListener;
    private ValueEventListener goalsListener;

    private DatabaseReference deviceLinesRef;
    private DatabaseReference todayCatsRef;
    private DatabaseReference monthHistoryRef;
    private DatabaseReference goalsRef;

    private String attachedTodayKey = null;

    private final Map<String, Boolean> lastLeak = new HashMap<>();
    private final Map<String, Boolean> lastLatched = new HashMap<>();
    private final Map<String, Boolean> valveFailure = new HashMap<>();
    private final Map<String, String> valvePendingTarget = new HashMap<>();
    private final Map<String, Long> valvePendingSince = new HashMap<>();
    private static final long VALVE_PENDING_TIMEOUT_MS = 10_000;
    private final Map<String, Boolean> limitAlertLocalGuard = new HashMap<>();


    private static final float FLOW_CONFIRM_EPS = 0.15f; // L/min threshold

    @Override
    protected int getLayoutResId() {
        return R.layout.activity_dashboard;
    }

    @Override
    protected int getCurrentNavItemId() {
        return R.id.nav_dashboard;
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        activeDeviceId = getCurrentDeviceId();

        uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) {
            Toast.makeText(this, "Session expired. Please log in again.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        dashboardStartMs = System.currentTimeMillis();

        bindViews();
        setupRecycler();

        setDeviceOnline(false);
        startBackgroundMotion();

        loadUserGoals();
        attachDeviceOnlineListener();
        attachDeviceLinesListener();
        attachTodayHistoryDailyListener();
        attachMonthlyUsageFromHistoryDaily();
    }

    @Override
    protected void onResume() {
        super.onResume();
        startBackgroundMotion();
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopBackgroundMotion();
    }

    @Override
    protected void onStop() {
        super.onStop();
        detachListeners();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopBackgroundMotion();
    }

    private void detachListeners() {
        if (deviceLinesRef != null && deviceLinesListener != null) {
            deviceLinesRef.removeEventListener(deviceLinesListener);
            deviceLinesListener = null;
        }

        if (todayCatsRef != null && todayHistoryListener != null) {
            todayCatsRef.removeEventListener(todayHistoryListener);
            todayHistoryListener = null;
        }

        if (monthHistoryRef != null && monthHistoryListener != null) {
            monthHistoryRef.removeEventListener(monthHistoryListener);
            monthHistoryListener = null;
        }

        if (goalsRef != null && goalsListener != null) {
            goalsRef.removeEventListener(goalsListener);
            goalsListener = null;
        }

        if (lastSeenRef != null && lastSeenListener != null) {
            lastSeenRef.removeEventListener(lastSeenListener);
            lastSeenListener = null;
        }
    }

    private void bindViews() {
        ivAnimatedBg = findViewById(R.id.ivAnimatedBg);

        chipStatus = findViewById(R.id.chipStatus);
        flowValue = findViewById(R.id.flowValue);
        usageValue = findViewById(R.id.usageValue);

        tvLineAFlow = findViewById(R.id.tvLineAFlow);
        chipLineAValve = findViewById(R.id.chipLineAValve);
        chipLineALeak = findViewById(R.id.chipLineALeak);
        btnLineAToggle = findViewById(R.id.btnLineAToggle);

        tvLineBFlow = findViewById(R.id.tvLineBFlow);
        chipLineBValve = findViewById(R.id.chipLineBValve);
        chipLineBLeak = findViewById(R.id.chipLineBLeak);
        btnLineBToggle = findViewById(R.id.btnLineBToggle);

        dailyProgress = findViewById(R.id.dailyProgress);
        monthlyProgress = findViewById(R.id.monthlyProgress);
        dailyProgressText = findViewById(R.id.dailyProgressText);
        monthlyProgressText = findViewById(R.id.monthlyProgressText);

        sensorsRecycler = findViewById(R.id.sensorsRecycler);
        categoryUsageRecycler = findViewById(R.id.categoryUsageRecycler);

        if (flowValue != null) flowValue.setText("0.00 L/min");
        if (usageValue != null) usageValue.setText("0.00 L");

        if (chipLineAValve != null) chipLineAValve.setText("VALVE: CLOSED");
        if (chipLineBValve != null) chipLineBValve.setText("VALVE: CLOSED");
        if (chipLineALeak != null) chipLineALeak.setText("LEAK: NO");
        if (chipLineBLeak != null) chipLineBLeak.setText("LEAK: NO");
    }

    private void setupRecycler() {
        if (sensorsRecycler != null) {
            sensorsRecycler.setLayoutManager(new LinearLayoutManager(this));
            sensorAdapter = new SensorsAdapter(sensorList, this::sendValveCommand);
            sensorsRecycler.setAdapter(sensorAdapter);
            sensorsRecycler.setNestedScrollingEnabled(false);
        }

        if (categoryUsageRecycler != null) {
            categoryUsageRecycler.setLayoutManager(
                    new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
            );
            categoryUsageAdapter = new CategoryUsageAdapter(categoryUsageRows);
            categoryUsageRecycler.setAdapter(categoryUsageAdapter);
            categoryUsageRecycler.setNestedScrollingEnabled(false);
        }
    }

    private void attachDeviceOnlineListener() {
        if (lastSeenListener != null) return;

        if (uid == null) return;

        lastSeenRef = FirebaseDatabase.getInstance()
                .getReference("hydroguard")
                .child("devices")
                .child(uid)
                .child(activeDeviceId)
                .child("meta")
                .child("lastSeen");

        lastSeenListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {

                Object v = snapshot.getValue();
                long lastSeen = 0L;

                if (v instanceof Number) {
                    lastSeen = ((Number) v).longValue();
                } else if (v instanceof String) {
                    try {
                        lastSeen = (long) Double.parseDouble(((String) v).trim());
                    } catch (Exception ignored) {}
                }

                boolean onlineNow = false;
                if (lastSeen > 0) {
                    long now = System.currentTimeMillis();
                    onlineNow = (now - lastSeen) <= OFFLINE_TIMEOUT_MS;
                }

                if (!onlineNow &&
                        System.currentTimeMillis() - dashboardStartMs < OFFLINE_GRACE_MS) {
                    setChip("CONNECTING…", 0xFFE5E7EB, 0xFF374151);
                    return;
                }

                setDeviceOnline(onlineNow);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                setDeviceOnline(false);
            }
        };

        lastSeenRef.addValueEventListener(lastSeenListener);
    }


    private void setDeviceOnline(boolean online) {
        isDeviceOnline = online;

        if (!online) {
            setChip("OFFLINE", 0xFFE2E8F0, 0xFF334155);

            if (btnLineAToggle != null) { btnLineAToggle.setEnabled(false); btnLineAToggle.setAlpha(0.55f); }
            if (btnLineBToggle != null) { btnLineBToggle.setEnabled(false); btnLineBToggle.setAlpha(0.55f); }
            return;
        }

        setChip("ONLINE", 0xFFE8F5E9, 0xFF2E7D32);

        if (btnLineAToggle != null) { btnLineAToggle.setEnabled(true); btnLineAToggle.setAlpha(1f); }
        if (btnLineBToggle != null) { btnLineBToggle.setEnabled(true); btnLineBToggle.setAlpha(1f); }
    }

    private void startBackgroundMotion() {
        final ImageView bg = ivAnimatedBg;
        if (bg == null) return;
        if (bgPanX != null && bgPanX.isRunning()) return;

        bg.setScaleX(1.06f);
        bg.setScaleY(1.06f);

        bgPanX = ObjectAnimator.ofFloat(bg, "translationX", 0f, -60f, 0f, 60f, 0f);
        bgPanX.setDuration(24000);
        bgPanX.setRepeatCount(ValueAnimator.INFINITE);
        bgPanX.setRepeatMode(ValueAnimator.RESTART);
        bgPanX.setInterpolator(new LinearInterpolator());

        bgPanY = ObjectAnimator.ofFloat(bg, "translationY", 0f, -40f, 0f, 40f, 0f);
        bgPanY.setDuration(28000);
        bgPanY.setRepeatCount(ValueAnimator.INFINITE);
        bgPanY.setRepeatMode(ValueAnimator.RESTART);
        bgPanY.setInterpolator(new LinearInterpolator());

        bgScaleX = ObjectAnimator.ofFloat(bg, "scaleX", 1.06f, 1.11f, 1.06f);
        bgScaleX.setDuration(18000);
        bgScaleX.setRepeatCount(ValueAnimator.INFINITE);
        bgScaleX.setRepeatMode(ValueAnimator.REVERSE);
        bgScaleX.setInterpolator(new AccelerateDecelerateInterpolator());

        bgScaleY = ObjectAnimator.ofFloat(bg, "scaleY", 1.06f, 1.11f, 1.06f);
        bgScaleY.setDuration(20000);
        bgScaleY.setRepeatCount(ValueAnimator.INFINITE);
        bgScaleY.setRepeatMode(ValueAnimator.REVERSE);
        bgScaleY.setInterpolator(new AccelerateDecelerateInterpolator());

        bgPanX.start();
        bgPanY.start();
        bgScaleX.start();
        bgScaleY.start();
    }

    private void stopBackgroundMotion() {
        if (bgPanX != null) bgPanX.cancel();
        if (bgPanY != null) bgPanY.cancel();
        if (bgScaleX != null) bgScaleX.cancel();
        if (bgScaleY != null) bgScaleY.cancel();

        bgPanX = null;
        bgPanY = null;
        bgScaleX = null;
        bgScaleY = null;
    }

    private void updateSystemStatusUI(boolean anyLeak, boolean anyWarning, boolean anyShutoff) {
        if (!isDeviceOnline) { setChip("OFFLINE", 0xFFE2E8F0, 0xFF334155); return; }

        if (anyShutoff) { setChip("SHUTOFF", 0xFFFFEBEE, 0xFFD32F2F); return; }
        if (anyLeak)    { setChip("LEAK",    0xFFFFEBEE, 0xFFD32F2F); return; }
        if (anyWarning) { setChip("WARNING", 0xFFFFF3E0, 0xFFFF8F00); return; }

        setChip("ONLINE", 0xFFE8F5E9, 0xFF2E7D32);
    }

    private void setChip(String text, int bgColor, int textColor) {
        if (chipStatus == null) return;
        chipStatus.setText(text);
        chipStatus.setChipBackgroundColor(ColorStateList.valueOf(bgColor));
        chipStatus.setTextColor(textColor);
    }

    private void attachDeviceLinesListener() {
        if (deviceLinesListener != null) return;

        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) return;

        deviceLinesRef = FirebaseDatabase.getInstance()
                .getReference("hydroguard")
                .child("devices")
                .child(uid)
                .child(activeDeviceId)
                .child("lines");

        deviceLinesListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {

                LineSnapshot showerSnap = readLineState(
                        snapshot.child(LINE_SHOWER).child("state"), LINE_SHOWER);
                LineSnapshot tapSnap = readLineState(
                        snapshot.child(LINE_TAP).child("state"), LINE_TAP);

                resolveValvePendingIfConfirmed(showerSnap);
                resolveValvePendingIfConfirmed(tapSnap);

                if (!alertStatePrimed) {
                    primeAlertState(showerSnap);
                    primeAlertState(tapSnap);
                    alertStatePrimed = true;
                } else {
                    detectAndCreateLineAlerts(showerSnap);
                    detectAndCreateLineAlerts(tapSnap);
                }

                SensorModel showerModel = toSensorModel(showerSnap);
                SensorModel tapModel = toSensorModel(tapSnap);

                sensorList.clear();
                sensorList.add(showerModel);
                sensorList.add(tapModel);
                if (sensorAdapter != null) sensorAdapter.notifyDataSetChanged();

                float combinedFlow = safe0(showerSnap.flow) + safe0(tapSnap.flow);
                if (flowValue != null) {
                    flowValue.setText(String.format(
                            Locale.getDefault(),
                            "%.2f L/min",
                            combinedFlow
                    ));
                }

                float combinedToday = safe0(showerSnap.todayUsage) + safe0(tapSnap.todayUsage);
                todayUsageLiters = combinedToday;
                if (usageValue != null) {
                    usageValue.setText(String.format(
                            Locale.getDefault(),
                            "%.2f L",
                            combinedToday
                    ));
                }

                renderLineCardA(showerModel);
                renderLineCardB(tapModel);

                boolean anyLeak = showerSnap.leak || tapSnap.leak;
                boolean anyWarning =
                        "WARNING".equalsIgnoreCase(showerSnap.mode) ||
                                "WARNING".equalsIgnoreCase(tapSnap.mode);
                boolean anyShutoff = showerSnap.isShutoff() || tapSnap.isShutoff();

                updateSystemStatusUI(anyLeak, anyWarning, anyShutoff);

                checkValveConfirmation(showerSnap);
                checkValveConfirmation(tapSnap);

                updateGoalsUIAndAlerts();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
            }
        };

        deviceLinesRef.addValueEventListener(deviceLinesListener);
    }

    private void primeAlertState(@NonNull LineSnapshot L) {
        lastLeak.put(L.lineId + "_leak", L.leak);
        lastLatched.put(L.lineId + "_latched", L.latched);
        valveFailure.put(L.lineId + "_close_fail", false);
    }

    private static class UiState {
        boolean enabled;
        String text;
        String command;
        String tone;

        static UiState from(@NonNull SensorModel.ValveUi ui) {
            UiState s = new UiState();
            s.enabled = ui.enabled;
            s.text = ui.text;
            s.command = ui.command;
            s.tone = (ui.tone != null) ? ui.tone.name() : "NEUTRAL";
            return s;
        }
    }

    private void renderLineCardA(@NonNull SensorModel m) {

        boolean pending = valvePendingSince.containsKey(m.getSafeLineLabel());

        if (tvLineAFlow != null) {
            tvLineAFlow.setText(String.format(Locale.getDefault(), "%.2f L/min", m.getFlow()));
        }

        if (chipLineAValve != null) {
            if (m.isValveOpen()) {
                chipLineAValve.setText(
                        m.getFlow() > FLOW_CONFIRM_EPS
                                ? "VALVE: OPEN (CONFIRMED)"
                                : "VALVE: OPEN"
                );
            } else {
                chipLineAValve.setText(
                        m.getFlow() <= FLOW_CONFIRM_EPS
                                ? "VALVE: CLOSED (CONFIRMED)"
                                : "VALVE: CLOSED (CHECK)"
                );
            }
        }

        if (chipLineALeak != null) {
            if (m.leak) {
                chipLineALeak.setText("LEAK DETECTED");
                chipLineALeak.setChipBackgroundColor(
                        ColorStateList.valueOf(Color.parseColor("#FCE8E6")));
            } else if (m.latched) {
                chipLineALeak.setText("RESET REQUIRED");
                chipLineALeak.setChipBackgroundColor(
                        ColorStateList.valueOf(Color.parseColor("#FFF4E5")));
            } else {
                chipLineALeak.setText("NO LEAK");
                chipLineALeak.setChipBackgroundColor(
                        ColorStateList.valueOf(Color.parseColor("#E6F4EA")));
            }
        }

        if (pending) {
            UiState pendingUi = new UiState();
            pendingUi.enabled = false;
            pendingUi.text = "MOVING…";
            pendingUi.command = "NONE";
            pendingUi.tone = "NEUTRAL";

            applyValveButtonStyle(btnLineAToggle, pendingUi);
            return;
        }

        UiState ui = UiState.from(m.computeValveUi());

        if (!m.isValveOpen()) {
            if (m.latched) {
                ui.text = "RESET";
                ui.command = "RESET";
                ui.enabled = true;
                ui.tone = "NEUTRAL";
            } else {
                ui.text = "CLOSED";
                ui.command = "NONE";
                ui.enabled = false;
                ui.tone = "NEUTRAL";
            }
        }

        if (!isDeviceOnline) {
            ui.enabled = false;
            ui.text = "OFFLINE";
            ui.command = "NONE";
            ui.tone = "NEUTRAL";
        }

        applyValveButtonStyle(btnLineAToggle, ui);

        if (btnLineAToggle != null) {
            btnLineAToggle.setOnClickListener(v -> {
                if (!isDeviceOnline) {
                    Toast.makeText(this, "Device offline", Toast.LENGTH_SHORT).show();
                    return;
                }

                SensorModel.ValveUi now = m.computeValveUi();
                if (!now.enabled || "NONE".equals(now.command)) return;

                sendValveCommandByLineId(
                        m.getSafeLineLabel(),
                        now.command,
                        m.leak,
                        m.getDisplayName()
                );
            });
        }
    }

    private void renderLineCardB(@NonNull SensorModel m) {

        boolean pending = valvePendingSince.containsKey(m.getSafeLineLabel());

        if (tvLineBFlow != null) {
            tvLineBFlow.setText(String.format(Locale.getDefault(), "%.2f L/min", m.getFlow()));
        }

        if (chipLineBValve != null) {
            if (m.isValveOpen()) {
                chipLineBValve.setText(
                        m.getFlow() > FLOW_CONFIRM_EPS
                                ? "VALVE: OPEN (CONFIRMED)"
                                : "VALVE: OPEN"
                );
            } else {
                chipLineBValve.setText(
                        m.getFlow() <= FLOW_CONFIRM_EPS
                                ? "VALVE: CLOSED (CONFIRMED)"
                                : "VALVE: CLOSED (CHECK)"
                );
            }
        }

        if (chipLineBLeak != null) {
            if (m.leak) {
                chipLineBLeak.setText("LEAK DETECTED");
                chipLineBLeak.setChipBackgroundColor(
                        ColorStateList.valueOf(Color.parseColor("#FCE8E6")));
            } else if (m.latched) {
                chipLineBLeak.setText("RESET REQUIRED");
                chipLineBLeak.setChipBackgroundColor(
                        ColorStateList.valueOf(Color.parseColor("#FFF4E5")));
            } else {
                chipLineBLeak.setText("NO LEAK");
                chipLineBLeak.setChipBackgroundColor(
                        ColorStateList.valueOf(Color.parseColor("#E6F4EA")));
            }
        }

        if (pending) {
            UiState pendingUi = new UiState();
            pendingUi.enabled = false;
            pendingUi.text = "MOVING…";
            pendingUi.command = "NONE";
            pendingUi.tone = "NEUTRAL";

            applyValveButtonStyle(btnLineBToggle, pendingUi);
            return;
        }

        UiState ui = UiState.from(m.computeValveUi());

        if (!m.isValveOpen()) {
            if (m.latched) {
                ui.text = "RESET";
                ui.command = "RESET";
                ui.enabled = true;
                ui.tone = "NEUTRAL";
            } else {
                ui.text = "CLOSED";
                ui.command = "NONE";
                ui.enabled = false;
                ui.tone = "NEUTRAL";
            }
        }

        if (!isDeviceOnline) {
            ui.enabled = false;
            ui.text = "OFFLINE";
            ui.command = "NONE";
            ui.tone = "NEUTRAL";
        }

        applyValveButtonStyle(btnLineBToggle, ui);

        if (btnLineBToggle != null) {
            btnLineBToggle.setOnClickListener(v -> {
                if (!isDeviceOnline) {
                    Toast.makeText(this, "Device offline", Toast.LENGTH_SHORT).show();
                    return;
                }

                SensorModel.ValveUi now = m.computeValveUi();
                if (!now.enabled || "NONE".equals(now.command)) return;

                sendValveCommandByLineId(
                        m.getSafeLineLabel(),
                        now.command,
                        m.leak,
                        m.getDisplayName()
                );
            });
        }
    }

    private void applyValveButtonStyle(@Nullable MaterialButton btn, @NonNull UiState ui) {
        if (btn == null) return;

        btn.setText(ui.text);
        btn.setEnabled(ui.enabled);
        btn.setAlpha(ui.enabled ? 1f : 0.55f);

        @DrawableRes int iconRes = iconForCommand(ui.command);
        if (iconRes != 0) btn.setIconResource(iconRes);
        else btn.setIcon(null);

        btn.setIconGravity(MaterialButton.ICON_GRAVITY_TEXT_START);
        btn.setIconPadding(dp(btn, 10));
        btn.setInsetTop(0);
        btn.setInsetBottom(0);

        int bg, fg, stroke;
        switch (ui.tone) {
            case "SUCCESS":
                bg = Color.parseColor("#E6F4EA");
                fg = Color.parseColor("#1E8E3E");
                stroke = Color.parseColor("#B7E1C1");
                break;
            case "DANGER":
                bg = Color.parseColor("#FCE8E6");
                fg = Color.parseColor("#D93025");
                stroke = Color.parseColor("#F6B8B3");
                break;
            case "NEUTRAL":
                bg = Color.parseColor("#FFF4E5");
                fg = Color.parseColor("#F57C00");
                stroke = Color.parseColor("#FFD199");
                break;
            default:
                bg = Color.parseColor("#F2F2F2");
                fg = Color.parseColor("#8A8A8A");
                stroke = Color.parseColor("#DDDDDD");
                break;
        }

        btn.setBackgroundTintList(ColorStateList.valueOf(bg));
        btn.setTextColor(fg);
        btn.setIconTint(ColorStateList.valueOf(fg));
        btn.setStrokeWidth(dp(btn, 1));
        btn.setStrokeColor(ColorStateList.valueOf(stroke));
    }

    @DrawableRes
    private int iconForCommand(@NonNull String command) {
        if ("OPEN".equals(command)) return R.drawable.ic_unlock;
        if ("CLOSE".equals(command)) return R.drawable.ic_lock;
        if ("RESET".equals(command)) return R.drawable.ic_refresh;
        if ("NONE".equals(command)) return 0;
        return R.drawable.ic_warning;
    }

    private int dp(@NonNull View v, int dp) {
        float d = v.getResources().getDisplayMetrics().density;
        return Math.round(dp * d);
    }

    private void sendValveCommand(@NonNull SensorModel sensor,
                                  @NonNull String command) {

        sendValveCommandByLineId(
                sensor.getSafeLineLabel(),
                command,
                sensor.leak,
                sensor.getDisplayName()
        );
    }

    private void sendValveCommandByLineId(@NonNull String lineId,
                                          @NonNull String command,
                                          boolean leakNow,
                                          @NonNull String displayName) {

        if (!isDeviceOnline) {
            Toast.makeText(this, "Device offline", Toast.LENGTH_SHORT).show();
            return;
        }

        if (valvePendingSince.containsKey(lineId)) {
            Toast.makeText(this, "Valve is moving…", Toast.LENGTH_SHORT).show();
            return;
        }

        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) {
            Toast.makeText(this, "Not logged in", Toast.LENGTH_SHORT).show();
            return;
        }

        if ("RESET".equals(command) && leakNow) {
            Toast.makeText(this, "Cannot reset: leak still detected", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!LINE_SHOWER.equals(lineId) && !LINE_TAP.equals(lineId)) {
            Toast.makeText(this, "Invalid line id", Toast.LENGTH_SHORT).show();
            return;
        }

        valvePendingSince.put(lineId, System.currentTimeMillis());
        valvePendingTarget.put(lineId, command);

        FirebaseDatabase.getInstance()
                .getReference("hydroguard")
                .child("devices")
                .child(uid)
                .child(activeDeviceId)
                .child("lines")
                .child(lineId)
                .child("cmd")
                .child("valve")
                .setValue(command);

        Toast.makeText(this, displayName + ": " + command, Toast.LENGTH_SHORT).show();
    }

    private static class LineSnapshot {
        String lineId;
        String sourceName;
        String sourceKey;

        float flow;
        float total;
        float todayUsage;
        String valve;
        String mode;
        boolean leak;
        boolean latched;

        boolean isShutoff() {
            return latched || "SHUTOFF".equalsIgnoreCase(mode);
        }
    }

    private LineSnapshot readLineState(@NonNull DataSnapshot stateSnap, @NonNull String lineId) {
        LineSnapshot L = new LineSnapshot();
        L.lineId = lineId;

        if (LINE_SHOWER.equals(lineId)) {
            L.sourceName = "Shower";
            L.sourceKey = "shower";
        } else {
            L.sourceName = "Tap";
            L.sourceKey = "tap";
        }

        L.flow = readFloatFlexible(stateSnap.child("flow").getValue());
        L.total = readFloatFlexible(stateSnap.child("total").getValue());
        L.todayUsage = readFloatFlexible(stateSnap.child("todayUsage").getValue());

        L.valve = safeText(stateSnap.child("valve").getValue(String.class), "CLOSED");
        L.mode  = safeText(stateSnap.child("mode").getValue(String.class), "NORMAL");

        Boolean leakB = stateSnap.child("leak").getValue(Boolean.class);
        Boolean latB  = stateSnap.child("latched").getValue(Boolean.class);

        L.leak = leakB != null && leakB;
        L.latched = latB != null && latB;

        return L;
    }

    private SensorModel toSensorModel(@NonNull LineSnapshot L) {
        SensorModel m = SensorModel.createForLine(L.lineId);
        m.flow = safe0(L.flow);
        m.total = safe0(L.total);
        m.todayUsage = safe0(L.todayUsage);
        m.valve = safeText(L.valve, "CLOSED");
        m.mode = safeText(L.mode, "NORMAL");
        m.leak = L.leak;
        m.latched = L.latched;
        m.name = m.getDisplayName();
        m.location = "-";
        return m;
    }

    private void detectAndCreateLineAlerts(@NonNull LineSnapshot L) {
        String prevLeakKey = L.lineId + "_leak";
        String prevLatKey  = L.lineId + "_latched";

        boolean prevLeakVal = lastLeak.containsKey(prevLeakKey) && Boolean.TRUE.equals(lastLeak.get(prevLeakKey));
        boolean prevLatVal  = lastLatched.containsKey(prevLatKey) && Boolean.TRUE.equals(lastLatched.get(prevLatKey));

        if (!prevLeakVal && L.leak) {
            createAlert(
                    "[" + activeDeviceId + "] Leak detected (" + L.sourceName + ")",
                    "Leak detected. Valve may auto-close for safety.",
                    "LEAK",
                    "RED",
                    L.lineId,
                    L.sourceKey
            );
        }

        if (prevLeakVal && !L.leak) {
            createAlert(
                    "[" + activeDeviceId + "] Leak cleared (" + L.sourceName + ")",
                    "Leak resolved. Valve can now be reset.",
                    "LEAK",
                    "GREEN",
                    L.lineId,
                    L.sourceKey
            );
        }

        if (!prevLatVal && L.latched) {
            createAlert(
                    "[" + activeDeviceId + "] Safety shutoff latched (" + L.sourceName + ")",
                    "Valve is latched closed. Reset is available only when leak is cleared.",
                    "SYSTEM",
                    "ORANGE",
                    L.lineId,
                    L.sourceKey
            );
        }

        if (prevLatVal && !L.latched) {
            createAlert(
                    "[" + activeDeviceId + "] Safety shutoff latched (" + L.sourceName + ")",
                    "Valve is no longer latched.",
                    "SYSTEM",
                    "GREEN",
                    L.lineId,
                    L.sourceKey
            );
        }

        lastLeak.put(prevLeakKey, L.leak);
        lastLatched.put(prevLatKey, L.latched);
    }

    private void createAlert(@NonNull String title,
                             @NonNull String details,
                             @NonNull String tag,
                             @NonNull String severity,
                             @NonNull String lineId,
                             @NonNull String sourceKey) {

        if (isFinishing() || isDestroyed()) return;

        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) return;

        String dayKey = dateKeyNow();
        long now = System.currentTimeMillis();

        String datetime = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                .format(Calendar.getInstance().getTime());

        DatabaseReference pushRef = FirebaseDatabase.getInstance()
                .getReference("hydroguard")
                .child("alerts")
                .child(uid)
                .child(dayKey)
                .push();

        if (pushRef.getKey() == null) return;

        Map<String, Object> payload = new HashMap<>();
        payload.put("title", title);
        payload.put("details", details);
        payload.put("datetime", datetime);
        payload.put("createdAt", now);
        payload.put("tag", tag);
        payload.put("severity", severity);
        payload.put("lineId", lineId);
        payload.put("deviceId", activeDeviceId);
        payload.put("source", sourceKey);

        pushRef.setValue(payload);

        if (!AppState.isForeground) {
            NotificationHelper.showLocalNotification(this, title, details);
        }

    }

    private void attachTodayHistoryDailyListener() {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) return;

        String todayKey = dateKeyNow();

        if (todayHistoryListener != null &&
                attachedTodayKey != null &&
                attachedTodayKey.equals(todayKey)) {
            return;
        }

        if (todayCatsRef != null && todayHistoryListener != null) {
            todayCatsRef.removeEventListener(todayHistoryListener);
        }

        attachedTodayKey = todayKey;

        todayCatsRef = FirebaseDatabase.getInstance()
                .getReference("hydroguard")
                .child("historyDaily")
                .child(uid)
                .child(todayKey)
                .child("categories");

        todayHistoryListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {

                Log.d("CATEGORY_DEBUG",
                        "exists=" + snapshot.exists() + " value=" + snapshot.getValue());

                // 🔹 CASE 1: No data yet for today
                if (!snapshot.exists()) {
                    categoryUsageRows.clear();
                    if (categoryUsageAdapter != null) {
                        categoryUsageAdapter.notifyDataSetChanged();
                    }
                    return;
                }

                float tapLiters = readFloatFlexible(snapshot.child("tap").getValue());
                float showerLiters = readFloatFlexible(snapshot.child("shower").getValue());

                // 🔹 CASE 2: Data exists but all zero
                if (tapLiters <= 0f && showerLiters <= 0f) {
                    categoryUsageRows.clear();
                    if (categoryUsageAdapter != null) {
                        categoryUsageAdapter.notifyDataSetChanged();
                    }
                    return;
                }

                ArrayList<CategoryUsageRow> tmp = new ArrayList<>();

                if (showerLiters > 0f) {
                    tmp.add(new CategoryUsageRow("Shower", showerLiters));
                }

                if (tapLiters > 0f) {
                    tmp.add(new CategoryUsageRow("Tap", tapLiters));
                }

                tmp.sort((a, b) -> Float.compare(b.liters, a.liters));

                categoryUsageRows.clear();
                categoryUsageRows.addAll(tmp);

                if (categoryUsageAdapter != null) {
                    categoryUsageAdapter.notifyDataSetChanged();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e("CATEGORY_DEBUG", "Cancelled", error.toException());
            }
        };

        todayCatsRef.addValueEventListener(todayHistoryListener);
    }

    private void attachMonthlyUsageFromHistoryDaily() {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) return;
        if (monthHistoryListener != null) return;

        monthHistoryRef = FirebaseDatabase.getInstance()
                .getReference("hydroguard")
                .child("historyDaily")
                .child(uid);

        monthHistoryListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                String monthKey = new SimpleDateFormat("yyyy-MM", Locale.getDefault())
                        .format(Calendar.getInstance().getTime());

                float sum = 0f;
                for (DataSnapshot daySnap : snapshot.getChildren()) {
                    String dateKey = daySnap.getKey();
                    if (dateKey == null) continue;
                    if (!dateKey.startsWith(monthKey)) continue;

                    float total = readFloatFlexible(daySnap.child("totalLiters").getValue());
                    sum += total;
                }

                monthUsageLiters = sum;
                updateGoalsUIAndAlerts();
                attachTodayHistoryDailyListener();
            }

            @Override public void onCancelled(@NonNull DatabaseError error) { }
        };

        monthHistoryRef.addValueEventListener(monthHistoryListener);
    }

    private void loadUserGoals() {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) return;

        goalsRef = FirebaseDatabase.getInstance()
                .getReference("hydroguard")
                .child("goals")
                .child(uid);

        goalsListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                dailyGoalLiters = toFloatSafe(snapshot.child("dailyGoal").getValue());
                monthlyGoalLiters = toFloatSafe(snapshot.child("monthlyGoal").getValue());
                updateGoalsUIAndAlerts();
            }
            @Override public void onCancelled(@NonNull DatabaseError error) { }
        };

        goalsRef.addValueEventListener(goalsListener);
    }

    private Float toFloatSafe(Object v) {
        if (v == null) return null;
        try {
            if (v instanceof Number) return ((Number) v).floatValue();
            if (v instanceof String) {
                String s = ((String) v).trim();
                if (s.isEmpty()) return null;
                return Float.parseFloat(s);
            }
        } catch (Exception ignored) {}
        return null;
    }

    private void updateGoalsUIAndAlerts() {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) return;

        String dayKey = dateKeyNow();

        /* ================= DAILY GOAL ================= */
        if (todayUsageLiters != null && dailyGoalLiters != null && dailyGoalLiters > 0f) {

            float ratio = todayUsageLiters / dailyGoalLiters;
            int percent = Math.min((int) (ratio * 100f), 100);

            // 🎨 Color by threshold
            applyGoalColor(dailyProgress, ratio, false);

            // 🎬 Smooth animation
            animateProgress(dailyProgress, percent);

            if (dailyProgressText != null) {
                dailyProgressText.setText(
                        String.format(
                                Locale.getDefault(),
                                "%.1f / %.1f L",
                                todayUsageLiters,
                                dailyGoalLiters
                        )
                );
            }

            float dailyLimit = dailyGoalLiters * DAILY_LIMIT_MULT;

            // 🔔 Alerts (UNCHANGED)
            maybeCreateLimitAlertOnce("daily50", dayKey,
                    "Daily usage 50%",
                    String.format(Locale.getDefault(),
                            "Today: %.1f L (Goal: %.1f L)", todayUsageLiters, dailyGoalLiters),
                    "LIMIT", "GREEN", todayUsageLiters, dailyGoalLiters * 0.5f);

            maybeCreateLimitAlertOnce("daily80", dayKey,
                    "Daily goal almost reached (80%)",
                    String.format(Locale.getDefault(),
                            "Today: %.1f L (Goal: %.1f L)", todayUsageLiters, dailyGoalLiters),
                    "LIMIT", "ORANGE", todayUsageLiters, dailyGoalLiters * 0.8f);

            maybeCreateLimitAlertOnce("daily100", dayKey,
                    "Daily goal exceeded",
                    String.format(Locale.getDefault(),
                            "Today: %.1f L (Goal: %.1f L)", todayUsageLiters, dailyGoalLiters),
                    "LIMIT", "RED", todayUsageLiters, dailyGoalLiters);

            maybeCreateLimitAlertOnce("dailyLimit", dayKey,
                    "Daily limit exceeded",
                    String.format(Locale.getDefault(),
                            "High usage: %.1f L (Limit: %.1f L). Possible leak or abnormal usage.",
                            todayUsageLiters, dailyLimit),
                    "LEAK", "RED", todayUsageLiters, dailyLimit);

        } else {
            if (dailyProgress != null) {
                animateProgress(dailyProgress, 0);
            }
            if (dailyProgressText != null) {
                dailyProgressText.setText("0 / 0 L");
            }
        }

        /* ================= MONTHLY GOAL ================= */
        if (monthUsageLiters != null && monthlyGoalLiters != null && monthlyGoalLiters > 0f) {

            float ratio = monthUsageLiters / monthlyGoalLiters;
            int percent = Math.min((int) (ratio * 100f), 100);

            // 🎨 Color by threshold
            applyGoalColor(monthlyProgress, ratio, true);

            // 🎬 Smooth animation
            animateProgress(monthlyProgress, percent);

            if (monthlyProgressText != null) {
                monthlyProgressText.setText(
                        String.format(
                                Locale.getDefault(),
                                "%.1f / %.1f L",
                                monthUsageLiters,
                                monthlyGoalLiters
                        )
                );
            }

            float monthlyLimit = monthlyGoalLiters * MONTHLY_LIMIT_MULT;
            String monthKey = new SimpleDateFormat("yyyy-MM", Locale.getDefault())
                    .format(Calendar.getInstance().getTime());

            // 🔔 Alerts (UNCHANGED)
            maybeCreateLimitAlertOnce("monthly50_" + monthKey, dayKey,
                    "Monthly usage 50%",
                    String.format(Locale.getDefault(),
                            "This month: %.1f L (Goal: %.1f L)", monthUsageLiters, monthlyGoalLiters),
                    "LIMIT", "GREEN", monthUsageLiters, monthlyGoalLiters * 0.5f);

            maybeCreateLimitAlertOnce("monthly80_" + monthKey, dayKey,
                    "Monthly goal almost reached (80%)",
                    String.format(Locale.getDefault(),
                            "This month: %.1f L (Goal: %.1f L)", monthUsageLiters, monthlyGoalLiters),
                    "LIMIT", "ORANGE", monthUsageLiters, monthlyGoalLiters * 0.8f);

            maybeCreateLimitAlertOnce("monthly100_" + monthKey, dayKey,
                    "Monthly goal exceeded",
                    String.format(Locale.getDefault(),
                            "This month: %.1f L (Goal: %.1f L)", monthUsageLiters, monthlyGoalLiters),
                    "LIMIT", "RED", monthUsageLiters, monthlyGoalLiters);

            maybeCreateLimitAlertOnce("monthlyLimit_" + monthKey, dayKey,
                    "Monthly limit exceeded",
                    String.format(Locale.getDefault(),
                            "High usage: %.1f L (Limit: %.1f L). Possible leak or abnormal usage.",
                            monthUsageLiters, monthlyLimit),
                    "LEAK", "RED", monthUsageLiters, monthlyLimit);

        } else {
            if (monthlyProgress != null) {
                animateProgress(monthlyProgress, 0);
            }
            if (monthlyProgressText != null) {
                monthlyProgressText.setText("0 / 0 L");
            }
        }
    }

    private void maybeCreateLimitAlertOnce(
            @NonNull String key,
            @NonNull String dayKey,
            @NonNull String title,
            @NonNull String details,
            @NonNull String tag,
            @NonNull String severity,
            float currentUsage,
            float threshold
    ) {
        if (currentUsage < threshold) return;

        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) return;

        // 🔒 LOCAL SESSION GUARD (prevents async race spam)
        String localKey = dayKey + "_" + key;
        if (Boolean.TRUE.equals(limitAlertLocalGuard.get(localKey))) return;

        DatabaseReference alertRef = FirebaseDatabase.getInstance()
                .getReference("hydroguard")
                .child("alerts")
                .child(uid)
                .child(dayKey)
                .child(key);   // 🔒 FIXED KEY

        alertRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {

                // 🔒 Already exists in DB → stop forever
                if (snapshot.exists()) {
                    limitAlertLocalGuard.put(localKey, true);
                    return;
                }

                limitAlertLocalGuard.put(localKey, true);

                createAlertWithFixedRef(
                        alertRef,
                        "[" + activeDeviceId + "] " + title,
                        details,
                        tag,
                        severity,
                        LINE_TAP,
                        "tap"
                );
            }

            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void createAlertWithFixedRef(
            @NonNull DatabaseReference ref,
            @NonNull String title,
            @NonNull String details,
            @NonNull String tag,
            @NonNull String severity,
            @NonNull String lineId,
            @NonNull String sourceKey
    ) {
        long now = System.currentTimeMillis();
        String datetime = new SimpleDateFormat(
                "yyyy-MM-dd HH:mm", Locale.getDefault()
        ).format(Calendar.getInstance().getTime());

        Map<String, Object> payload = new HashMap<>();
        payload.put("title", title);
        payload.put("details", details);
        payload.put("datetime", datetime);
        payload.put("createdAt", now);
        payload.put("tag", tag);
        payload.put("severity", severity);
        payload.put("lineId", lineId);
        payload.put("deviceId", activeDeviceId);
        payload.put("source", sourceKey);

        ref.setValue(payload);

        if (!AppState.isForeground) {
            NotificationHelper.showLocalNotification(this, title, details);
        }
    }



    private float readFloatFlexible(Object v) {
        try {
            if (v instanceof Number) return ((Number) v).floatValue();
            if (v instanceof String) return Float.parseFloat(((String) v).trim());
        } catch (Exception ignored) {}
        return 0f;
    }

    private String dateKeyNow() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                .format(Calendar.getInstance().getTime());
    }

    private String safeText(String s, String fallback) {
        if (s == null) return fallback;
        String v = s.trim();
        return v.isEmpty() ? fallback : v;
    }

    private float safe0(float v) {
        if (Float.isNaN(v) || Float.isInfinite(v)) return 0f;
        return Math.max(0f, v);
    }

    private void resolveValvePendingIfConfirmed(@NonNull LineSnapshot L) {

        Long since = valvePendingSince.get(L.lineId);
        String target = valvePendingTarget.get(L.lineId);
        if (since == null || target == null) return;

        long now = System.currentTimeMillis();
        long elapsed = now - since;

        Log.d("VALVE_PENDING",
                "line=" + L.lineId +
                        " target=" + target +
                        " valve=" + L.valve +
                        " flow=" + L.flow +
                        " elapsed=" + elapsed
        );

        boolean stateConfirmed =
                ("OPEN".equalsIgnoreCase(target) && "OPEN".equalsIgnoreCase(L.valve)) ||
                        ("CLOSE".equalsIgnoreCase(target) && "CLOSED".equalsIgnoreCase(L.valve));

        if (stateConfirmed) {
            valvePendingSince.remove(L.lineId);
            valvePendingTarget.remove(L.lineId);
            Log.d("VALVE_PENDING", "Confirmed by valve state: " + L.lineId);
            return;
        }

        if ("CLOSE".equalsIgnoreCase(target)
                && elapsed > 300
                && L.flow <= FLOW_CONFIRM_EPS
        ) {
            valvePendingSince.remove(L.lineId);
            valvePendingTarget.remove(L.lineId);
            Log.d("VALVE_PENDING", "Confirmed by flow drop: " + L.lineId);
            return;
        }

        if (elapsed > VALVE_PENDING_TIMEOUT_MS) {

            // 🛑 SAFE GUARD: device offline → do NOT alert
            if (!isDeviceOnline) {
                valvePendingSince.remove(L.lineId);
                valvePendingTarget.remove(L.lineId);
                Log.d("VALVE_PENDING", "Timeout ignored (device offline): " + L.lineId);
                return;
            }

            valvePendingSince.remove(L.lineId);
            valvePendingTarget.remove(L.lineId);

            Toast.makeText(
                    this,
                    "Valve did not respond",
                    Toast.LENGTH_SHORT
            ).show();

            createAlert(
                    "[" + activeDeviceId + "] Valve response timeout (" + L.sourceName + ")",
                    "No confirmation received from device. Please check connectivity.",
                    "VALVE",
                    "ORANGE",
                    L.lineId,
                    L.sourceKey
            );

            Log.w("VALVE_PENDING", "Timeout for valve: " + L.lineId);
        }
    }

    private void checkValveConfirmation(@NonNull LineSnapshot L) {

        boolean failed =
                "CLOSED".equalsIgnoreCase(L.valve) &&
                        L.flow > FLOW_CONFIRM_EPS;

        if (!failed) {
            valveFailure.put(L.lineId + "_close_fail", false);
            return;
        }

        // 🔒 One alert per day per line
        maybeCreateLimitAlertOnce(
                "valveFail_" + L.lineId,
                dateKeyNow(),
                "Valve closure failed (" + L.sourceName + ")",
                "Valve set to CLOSED but flow detected (" +
                        String.format(Locale.getDefault(), "%.2f L/min", L.flow) +
                        "). Possible valve fault or obstruction.",
                "VALVE",
                "RED",
                1f,
                0f   // always triggers once
        );

        valveFailure.put(L.lineId + "_close_fail", true);
    }

    private void animateProgress(@NonNull ProgressBar bar, int target) {
        int start = bar.getProgress();
        ObjectAnimator anim = ObjectAnimator.ofInt(bar, "progress", start, target);
        anim.setDuration(700);
        anim.setInterpolator(new AccelerateDecelerateInterpolator());
        anim.start();
    }

    private void applyGoalColor(@NonNull ProgressBar bar, float ratio, boolean monthly) {

        @DrawableRes int drawable;

        if (ratio >= 1f) {
            // 🔴 100%+
            drawable = R.drawable.progress_rounded_red;
        } else if (ratio >= 0.8f) {
            // 🟠 80%+
            drawable = R.drawable.progress_rounded_orange;
        } else {
            // 🟢 Normal
            drawable = monthly
                    ? R.drawable.progress_rounded_teal
                    : R.drawable.progress_rounded_secondary;
        }

        bar.setProgressDrawable(
                androidx.core.content.ContextCompat.getDrawable(this, drawable)
        );

    }
}
