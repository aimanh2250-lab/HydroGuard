package com.example.hydroguard;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.ImageView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.splashscreen.SplashScreen;

import com.airbnb.lottie.LottieAnimationView;

public class SplashActivity extends AppCompatActivity {

    private boolean navigated = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        SplashScreen.installSplashScreen(this);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        LottieAnimationView water = findViewById(R.id.waterSplash);
        ImageView logo = findViewById(R.id.imgLogo);

        water.setRepeatCount(0);
        water.setSpeed(0.6f);
        water.playAnimation();

        logo.animate()
                .alpha(1f)
                .setDuration(900)
                .start();

        water.addAnimatorListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                goNext();
            }
        });

        new Handler(Looper.getMainLooper()).postDelayed(
                this::goNext,
                5500
        );
    }

    private void goNext() {
        if (navigated) return;
        navigated = true;

        startActivity(new Intent(this, MainActivity.class));
        finish();
    }
}
