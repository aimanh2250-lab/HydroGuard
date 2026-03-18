package com.example.hydroguard;

import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

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
import java.util.Comparator;
import java.util.Locale;

public class AlertsActivity extends BaseDrawerActivity {

    private RecyclerView alertsRecycler;
    private View tvEmpty;

    private final ArrayList<AlertModel> list = new ArrayList<>();
    private AlertsAdapter adapter;

    private DatabaseReference alertsRef;
    private ValueEventListener alertsListener;

    private String uid;
    private String dayKey;

    @Override
    protected int getLayoutResId() {
        return R.layout.activity_alerts;
    }

    @Override
    protected int getCurrentNavItemId() {
        return R.id.nav_alerts;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        alertsRecycler = findViewById(R.id.alertsRecycler);
        tvEmpty = findViewById(R.id.tvEmpty);

        alertsRecycler.setLayoutManager(new LinearLayoutManager(this));
        alertsRecycler.setNestedScrollingEnabled(false);

        adapter = new AlertsAdapter(new ArrayList<>());
        alertsRecycler.setAdapter(adapter);

        uid = FirebaseAuth.getInstance().getUid();
        dayKey = dateKeyNow();

        showEmpty(true);
    }

    @Override
    protected void onStart() {
        super.onStart();
        attachAlertsListener();
    }

    @Override
    protected void onStop() {
        super.onStop();
        detachAlertsListener();
    }

    private void attachAlertsListener() {
        if (uid == null) {
            list.clear();
            adapter.submitList(new ArrayList<>());
            showEmpty(true);
            return;
        }

        detachAlertsListener();

        alertsRef = FirebaseDatabase.getInstance()
                .getReference("hydroguard")
                .child("alerts")
                .child(uid)
                .child(dayKey);

        alertsListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {

                ArrayList<AlertModel> tmp = new ArrayList<>();

                for (DataSnapshot alertSnap : snapshot.getChildren()) {
                    AlertModel m = alertSnap.getValue(AlertModel.class);
                    if (m == null) continue;

                    m.pushId = alertSnap.getKey();
                    m.dayKey = dayKey;

                    normalizeAlert(m);

                    tmp.add(m);
                }

                sortNewestFirst(tmp);

                list.clear();
                list.addAll(tmp);

                adapter.submitList(new ArrayList<>(list));
                showEmpty(list.isEmpty());
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                showEmpty(list.isEmpty());
            }
        };

        alertsRef.addValueEventListener(alertsListener);
    }

    private void detachAlertsListener() {
        if (alertsRef != null && alertsListener != null) {
            alertsRef.removeEventListener(alertsListener);
        }
        alertsRef = null;
        alertsListener = null;
    }

    private void sortNewestFirst(ArrayList<AlertModel> items) {
        Collections.sort(items, new Comparator<AlertModel>() {
            @Override
            public int compare(AlertModel a, AlertModel b) {
                long ta = timeSafe(a);
                long tb = timeSafe(b);
                return Long.compare(tb, ta);
            }
        });
    }

    private long timeSafe(AlertModel m) {
        if (m == null) return 0L;
        if (m.createdAt != null) return m.createdAt;
        return 0L;
    }

    private void normalizeAlert(@NonNull AlertModel m) {
        m.title = safeTrim(m.title);
        m.details = safeTrim(m.details);
        m.datetime = safeTrim(m.datetime);

        m.tag = safeUpper(m.tag);
        m.severity = safeUpper(m.severity);

        m.lineId = safeUpper(m.lineId);
        m.deviceId = safeUpper(m.deviceId);
        m.source = safeLower(m.source);

        if (m.tag.isEmpty()) m.tag = "SYSTEM";
        if (m.severity.isEmpty()) m.severity = "GRAY";
        if (m.deviceId.isEmpty()) m.deviceId = "HG001";
    }

    private String dateKeyNow() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                .format(Calendar.getInstance().getTime());
    }

    private String safeTrim(String s) {
        return s == null ? "" : s.trim();
    }

    private String safeUpper(String s) {
        String t = safeTrim(s);
        return t.isEmpty() ? "" : t.toUpperCase(Locale.ROOT);
    }

    private String safeLower(String s) {
        String t = safeTrim(s);
        return t.isEmpty() ? "" : t.toLowerCase(Locale.ROOT);
    }

    private void showEmpty(boolean empty) {
        if (tvEmpty != null) tvEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
        if (alertsRecycler != null) alertsRecycler.setVisibility(empty ? View.GONE : View.VISIBLE);
    }
}
