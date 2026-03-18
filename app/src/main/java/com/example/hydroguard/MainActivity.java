package com.example.hydroguard;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // ✅ REQUIRED: attach a layout to avoid WindowManager crash
        setContentView(R.layout.activity_main);

        // ✅ Initialize Firebase safely (idempotent)
        FirebaseApp.initializeApp(this);

        // ✅ Route user after init
        routeUser();
    }

    private boolean routed = false;

    private void routeUser() {
        if (routed) return;
        routed = true;

        if (FirebaseAuth.getInstance().getCurrentUser() != null) {
            BaseDrawerActivity.resetBaseSessionFlags();
            startActivity(new Intent(this, DashboardActivity.class));
        } else {
            startActivity(new Intent(this, LoginActivity.class));
        }
        finish();
    }

}
