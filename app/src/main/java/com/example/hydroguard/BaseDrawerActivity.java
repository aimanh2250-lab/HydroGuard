package com.example.hydroguard;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.ImageView;

import androidx.annotation.IdRes;
import androidx.annotation.LayoutRes;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import android.view.animation.LinearInterpolator;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.navigation.NavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.util.HashMap;
import java.util.Map;

public abstract class BaseDrawerActivity extends AppCompatActivity {

    // ===================== UI =====================
    protected DrawerLayout drawerLayout;
    protected NavigationView navigationView;
    protected MaterialToolbar toolbar;
    protected ImageView ivAnimatedBg;

    private AnimatorSet bgAnim;

    // ===================== ABSTRACT =====================
    @LayoutRes
    protected abstract int getLayoutResId();

    @IdRes
    protected abstract int getCurrentNavItemId();

    // ===================== DEVICE SESSION =====================
    protected static final String[] DEFAULT_DEVICE_IDS = new String[]{"HG001"};
    protected static String currentDeviceId = "HG001";

    private static boolean devicesAutoPaired = false;
    private static boolean userDefaultsEnsured = false;

    // ===================== LIFECYCLE =====================
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(getLayoutResId());

        drawerLayout = findViewById(R.id.drawerLayout);
        navigationView = findViewById(R.id.navigationView);
        toolbar = findViewById(R.id.toolbar);
        ivAnimatedBg = findViewById(R.id.ivAnimatedBg);

        setupDrawer();

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            ensureUserDefaultsOnce(user.getUid());
            autoPairDefaultDevicesOnce(user.getUid());
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        AppState.isForeground = true;
        if (ivAnimatedBg != null) startBgMotion(ivAnimatedBg);
    }

    @Override
    protected void onPause() {
        super.onPause();
        AppState.isForeground = false;
        stopBgMotion();
    }

    // ===================== BACKGROUND ANIMATION =====================
    private void startBgMotion(ImageView bg) {
        stopBgMotion();

        ObjectAnimator moveX = ObjectAnimator.ofFloat(bg, "translationX", -35f, 35f);
        ObjectAnimator moveY = ObjectAnimator.ofFloat(bg, "translationY", -25f, 25f);
        ObjectAnimator scaleX = ObjectAnimator.ofFloat(bg, "scaleX", 1.05f, 1.12f);
        ObjectAnimator scaleY = ObjectAnimator.ofFloat(bg, "scaleY", 1.05f, 1.12f);

        moveX.setDuration(6000);
        moveY.setDuration(5200);
        scaleX.setDuration(7200);
        scaleY.setDuration(7200);

        moveX.setRepeatCount(ValueAnimator.INFINITE);
        moveY.setRepeatCount(ValueAnimator.INFINITE);
        scaleX.setRepeatCount(ValueAnimator.INFINITE);
        scaleY.setRepeatCount(ValueAnimator.INFINITE);

        moveX.setRepeatMode(ValueAnimator.REVERSE);
        moveY.setRepeatMode(ValueAnimator.REVERSE);
        scaleX.setRepeatMode(ValueAnimator.REVERSE);
        scaleY.setRepeatMode(ValueAnimator.REVERSE);

        moveX.setInterpolator(new LinearInterpolator());
        moveY.setInterpolator(new LinearInterpolator());
        scaleX.setInterpolator(new LinearInterpolator());
        scaleY.setInterpolator(new LinearInterpolator());

        bgAnim = new AnimatorSet();
        bgAnim.playTogether(moveX, moveY, scaleX, scaleY);
        bgAnim.start();
    }

    private void stopBgMotion() {
        if (bgAnim != null) {
            bgAnim.cancel();
            bgAnim = null;
        }
    }

    // ===================== DEVICE AUTO-PAIR =====================
    private void autoPairDefaultDevicesOnce(String uid) {
        if (devicesAutoPaired) return;
        devicesAutoPaired = true;

        for (String deviceId : DEFAULT_DEVICE_IDS) {
            DevicePairingManager.autoPairDevice(deviceId, new DevicePairingManager.PairCallback() {
                @Override
                public void onPaired(String pairedDeviceId) {
                    FirebaseDatabase.getInstance()
                            .getReference("hydroguard/users")
                            .child(uid)
                            .child("devices")
                            .child(pairedDeviceId)
                            .setValue(true);

                    if (currentDeviceId == null || currentDeviceId.trim().isEmpty()) {
                        currentDeviceId = pairedDeviceId;
                    }

                    Log.d("HydroGuard", "Auto paired: " + pairedDeviceId);
                }

                @Override
                public void onAlreadyPairedByOther(String pairedDeviceId) {
                    Log.w("HydroGuard", "Device owned by another user: " + pairedDeviceId);
                }

                @Override
                public void onError(String deviceId, String msg) {
                    Log.e("HydroGuard", "Pair error " + deviceId + ": " + msg);
                }
            });
        }
    }

    protected String getCurrentDeviceId() {
        return (currentDeviceId == null || currentDeviceId.trim().isEmpty())
                ? "HG001"
                : currentDeviceId;
    }

    // ===================== DRAWER =====================
    private void setupDrawer() {
        if (toolbar != null) setSupportActionBar(toolbar);

        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this,
                drawerLayout,
                toolbar,
                R.string.navigation_drawer_open,
                R.string.navigation_drawer_close
        );

        if (drawerLayout != null) {
            drawerLayout.addDrawerListener(toggle);
            toggle.syncState();
        }

        if (navigationView != null) {
            navigationView.setCheckedItem(getCurrentNavItemId());

            navigationView.setNavigationItemSelectedListener(item -> {
                int id = item.getItemId();

                if (id == getCurrentNavItemId()) {
                    closeDrawer();
                    return true;
                }

                if (id == R.id.nav_dashboard) openActivity(DashboardActivity.class, true);
                else if (id == R.id.nav_history) openActivity(HistoryActivity.class, true);
                else if (id == R.id.nav_insights) openActivity(InsightsActivity.class, true);
                else if (id == R.id.nav_billing) openActivity(BillingActivity.class, true);
                else if (id == R.id.nav_alerts) openActivity(AlertsActivity.class, true);
                else if (id == R.id.nav_settings) openActivity(SettingsActivity.class, false);

                closeDrawer();
                return true;
            });
        }
    }

    protected void openActivity(Class<?> cls, boolean finishCurrent) {
        Intent intent = new Intent(this, cls);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        if (finishCurrent) finish();
    }

    private void closeDrawer() {
        if (drawerLayout != null && drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START);
        }
    }

    @Override
    public void onBackPressed() {
        if (drawerLayout != null && drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }

    // ===================== USER DEFAULTS =====================
    private void ensureUserDefaultsOnce(String uid) {
        if (userDefaultsEnsured) return;
        userDefaultsEnsured = true;

        DatabaseReference ref = FirebaseDatabase.getInstance()
                .getReference("hydroguard")
                .child("goals")
                .child(uid);

        ref.get().addOnSuccessListener(snapshot -> {
            Map<String, Object> updates = new HashMap<>();
            if (!snapshot.hasChild("dailyGoal")) updates.put("dailyGoal", 50);
            if (!snapshot.hasChild("monthlyGoal")) updates.put("monthlyGoal", 1500);
            if (!updates.isEmpty()) ref.updateChildren(updates);
        });
    }

    // ===================== SESSION RESET (CALL ON LOGOUT) =====================
    public static void resetBaseSessionFlags() {
        devicesAutoPaired = false;
        userDefaultsEnsured = false;
        currentDeviceId = "HG001";
    }
}
