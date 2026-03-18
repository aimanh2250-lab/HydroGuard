package com.example.hydroguard;

import android.content.Intent;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.Toast;
import android.widget.TextView;

import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.FirebaseDatabase;

import java.util.HashMap;

public class SettingsActivity extends BaseDrawerActivity {

    private EditText dailyGoalInput, monthlyGoalInput;

    private MaterialButton saveGoalsButton;
    private MaterialButton logoutButton;
    private MaterialButton faqButton;

    private TextView tvProfileName, tvProfileEmail, tvProfilePhone, tvProfileUid;
    private MaterialButton btnEditProfile;

    private FirebaseDatabase db;
    private FirebaseAuth auth;

    @Override
    protected int getLayoutResId() {
        return R.layout.activity_settings;
    }

    @Override
    protected int getCurrentNavItemId() {
        return R.id.nav_settings;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        db = FirebaseDatabase.getInstance();
        auth = FirebaseAuth.getInstance();

        dailyGoalInput   = findViewById(R.id.dailyGoalInput);
        monthlyGoalInput = findViewById(R.id.monthlyGoalInput);
        saveGoalsButton  = findViewById(R.id.saveGoalsButton);

        logoutButton = findViewById(R.id.logoutButton);
        faqButton    = findViewById(R.id.faqButton);

        tvProfileName  = findViewById(R.id.tvProfileName);
        tvProfileEmail = findViewById(R.id.tvProfileEmail);
        tvProfilePhone = findViewById(R.id.tvProfilePhone);
        tvProfileUid   = findViewById(R.id.tvProfileUid);
        btnEditProfile = findViewById(R.id.btnEditProfile);

        loadProfile();
        loadGoals();

        saveGoalsButton.setOnClickListener(v -> saveGoals());
        logoutButton.setOnClickListener(v -> logout());
        btnEditProfile.setOnClickListener(v -> openEditProfile());
        faqButton.setOnClickListener(v -> openFaq());
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadProfile();
    }

    private void loadProfile() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) return;

        String email = user.getEmail();
        String uid = user.getUid();

        tvProfileEmail.setText((email != null) ? email : "No email");
        tvProfileUid.setText(uid);

        String authName = user.getDisplayName();
        String fallbackName = (authName != null && !authName.trim().isEmpty()) ? authName : "HydroGuard User";
        tvProfileName.setText(fallbackName);
        tvProfilePhone.setText("Phone not set");

        db.getReference("hydroguard/users/" + uid + "/profile")
                .get()
                .addOnSuccessListener(snapshot -> {
                    if (!snapshot.exists()) return;

                    Object nameV = snapshot.child("name").getValue();
                    Object phoneV = snapshot.child("phone").getValue();

                    if (nameV != null) {
                        String rtdbName = String.valueOf(nameV).trim();
                        if (!rtdbName.isEmpty()) tvProfileName.setText(rtdbName);
                    }

                    if (phoneV != null) {
                        String phone = String.valueOf(phoneV).trim();
                        tvProfilePhone.setText(phone.isEmpty() ? "Phone not set" : phone);
                    }
                });
    }

    private void loadGoals() {
        String uid = auth.getUid();
        if (uid == null) return;

        db.getReference("hydroguard/goals/" + uid)
                .get()
                .addOnSuccessListener(snapshot -> {
                    if (!snapshot.exists()) return;

                    String daily = toStringSafe(snapshot.child("dailyGoal").getValue());
                    String monthly = toStringSafe(snapshot.child("monthlyGoal").getValue());

                    if (daily != null) dailyGoalInput.setText(daily);
                    if (monthly != null) monthlyGoalInput.setText(monthly);
                });
    }

    private void saveGoals() {
        String uid = auth.getUid();
        if (uid == null) {
            Toast.makeText(this, "User not logged in", Toast.LENGTH_SHORT).show();
            return;
        }

        String dailyGoalStr = dailyGoalInput.getText().toString().trim();
        String monthlyGoalStr = monthlyGoalInput.getText().toString().trim();

        Float dailyGoal = dailyGoalStr.isEmpty() ? null : parsePositiveFloat(dailyGoalStr);
        Float monthlyGoal = monthlyGoalStr.isEmpty() ? null : parsePositiveFloat(monthlyGoalStr);

        if (dailyGoalStr.length() > 0 && dailyGoal == null) {
            Toast.makeText(this, "Daily goal must be a number > 0", Toast.LENGTH_SHORT).show();
            return;
        }

        if (monthlyGoalStr.length() > 0 && monthlyGoal == null) {
            Toast.makeText(this, "Monthly goal must be a number > 0", Toast.LENGTH_SHORT).show();
            return;
        }

        HashMap<String, Object> map = new HashMap<>();

        if (dailyGoal != null) {
            map.put("dailyGoal", dailyGoal);
        }

        if (monthlyGoal != null) {
            map.put("monthlyGoal", monthlyGoal);
        }

        if (map.isEmpty()) {
            Toast.makeText(this, "Enter at least one goal", Toast.LENGTH_SHORT).show();
            return;
        }

        db.getReference("hydroguard/goals/" + uid)
                .setValue(map)
                .addOnSuccessListener(unused ->
                        Toast.makeText(this, "Usage goals saved", Toast.LENGTH_SHORT).show()
                )
                .addOnFailureListener(e ->
                        Toast.makeText(this, e.getMessage(), Toast.LENGTH_SHORT).show()
                );
    }

    private void logout() {
        auth.signOut();
        Toast.makeText(this, "Logged out", Toast.LENGTH_SHORT).show();

        Intent intent = new Intent(SettingsActivity.this, LoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void openEditProfile() {
        startActivity(new Intent(SettingsActivity.this, EditProfileActivity.class));
    }

    private void openFaq() {
        startActivity(new Intent(SettingsActivity.this, FaqActivity.class));
    }

    private Float parsePositiveFloat(String s) {
        try {
            float v = Float.parseFloat(s);
            return (v > 0f) ? v : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private String toStringSafe(Object v) {
        if (v == null) return null;
        if (v instanceof Number) return String.valueOf(((Number) v).floatValue());
        return String.valueOf(v);
    }
}
