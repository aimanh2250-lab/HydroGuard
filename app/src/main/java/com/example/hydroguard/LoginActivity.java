package com.example.hydroguard;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.TextWatcher;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;

public class LoginActivity extends AppCompatActivity {

    private TextInputLayout emailLayout, passwordLayout;
    private TextInputEditText emailField, passwordField;

    private MaterialButton loginBtn;
    private View loginLoading;
    private TextView tvPasswordStrength;
    private TextView registerTxt, tvForgot;

    private FirebaseAuth auth;

    private final ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.RequestPermission(),
                    granted -> {}
            );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        bindViews();
        auth = FirebaseAuth.getInstance();

        playEntranceAnimation();
        makeOnlyRegisterClickable();
        attachFieldWatchers();
        requestNotificationPermissionIfNeeded();

        loginBtn.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            loginUser();
        });

        tvForgot.setOnClickListener(v -> forgotPassword());
    }

    private void loginUser() {
        clearFieldErrors();

        String email = getText(emailField);
        String pass = getText(passwordField);

        boolean ok = true;

        if (email.isEmpty()) {
            emailLayout.setError("Email is required");
            ok = false;
        } else if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            emailLayout.setError("Enter a valid email");
            ok = false;
        }

        if (pass.isEmpty()) {
            passwordLayout.setError("Password is required");
            ok = false;
        } else if (pass.length() < 6) {
            passwordLayout.setError("Password is too short");
            ok = false;
        }

        if (!ok) {
            loginBtn.performHapticFeedback(HapticFeedbackConstants.REJECT);
            return;
        }

        setLoading(true);

        auth.signInWithEmailAndPassword(email, pass)
                .addOnSuccessListener(result -> {
                    setLoading(false);
                    startDashboard();
                })
                .addOnFailureListener(e -> {
                    setLoading(false);
                    Toast.makeText(this, e.getMessage(), Toast.LENGTH_SHORT).show();
                    loginBtn.performHapticFeedback(HapticFeedbackConstants.REJECT);
                });
    }

    private void startDashboard() {
        Intent intent = new Intent(this, DashboardActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return;

        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED) return;

        requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
    }

    private void bindViews() {
        emailLayout = findViewById(R.id.emailLayout);
        passwordLayout = findViewById(R.id.passwordLayout);
        emailField = findViewById(R.id.emailField);
        passwordField = findViewById(R.id.passwordField);
        loginBtn = findViewById(R.id.loginBtn);
        loginLoading = findViewById(R.id.loginLoading);
        registerTxt = findViewById(R.id.registerTxt);
        tvForgot = findViewById(R.id.tvForgot);
        tvPasswordStrength = findViewById(R.id.tvPasswordStrength);
    }

    private void setLoading(boolean loading) {
        loginBtn.setEnabled(!loading);
        loginBtn.animate().alpha(loading ? 0.8f : 1f).setDuration(150).start();
        loginBtn.setText(loading ? "Signing in…" : "Login");
        loginLoading.setVisibility(loading ? View.VISIBLE : View.GONE);
    }

    private void clearFieldErrors() {
        emailLayout.setError(null);
        passwordLayout.setError(null);
    }

    private void attachFieldWatchers() {
        emailField.addTextChangedListener(simpleWatcher(() ->
                emailLayout.setError(null)
        ));

        passwordField.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            public void afterTextChanged(Editable s) {}

            public void onTextChanged(CharSequence s, int start, int before, int count) {
                passwordLayout.setError(null);
                updatePasswordStrength(s.toString());
            }
        });
    }

    private void updatePasswordStrength(String password) {
        if (password.isEmpty()) {
            tvPasswordStrength.animate()
                    .alpha(0f)
                    .scaleY(0.8f)
                    .setDuration(120)
                    .withEndAction(() -> tvPasswordStrength.setVisibility(View.GONE))
                    .start();
            return;
        }

        if (tvPasswordStrength.getVisibility() != View.VISIBLE) {
            tvPasswordStrength.setVisibility(View.VISIBLE);
            tvPasswordStrength.setAlpha(0f);
            tvPasswordStrength.setScaleY(0.8f);
            tvPasswordStrength.animate()
                    .alpha(1f)
                    .scaleY(1f)
                    .setDuration(180)
                    .start();
        }

        int score = 0;
        if (password.length() >= 8) score++;
        if (password.matches(".*[A-Z].*")) score++;
        if (password.matches(".*[a-z].*")) score++;
        if (password.matches(".*\\d.*")) score++;
        if (password.matches(".*[!@#$%^&*+=?-].*")) score++;

        if (score <= 2) {
            tvPasswordStrength.setText("Weak password");
            tvPasswordStrength.setTextColor(Color.parseColor("#EF4444"));
        } else if (score <= 4) {
            tvPasswordStrength.setText("Good password");
            tvPasswordStrength.setTextColor(Color.parseColor("#F59E0B"));
        } else {
            tvPasswordStrength.setText("Strong password ✓");
            tvPasswordStrength.setTextColor(Color.parseColor("#22C55E"));
        }
    }

    private void playEntranceAnimation() {
        View card = findViewById(R.id.loginCard);

        card.setAlpha(0f);
        card.setTranslationY(80f);

        card.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(650)
                .setStartDelay(120)
                .start();
    }

    private void makeOnlyRegisterClickable() {
        SpannableString ss = new SpannableString("Don’t have an account? Register");
        int start = ss.toString().indexOf("Register");

        ss.setSpan(new ClickableSpan() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(LoginActivity.this, RegisterActivity.class));
            }

            @Override
            public void updateDrawState(TextPaint ds) {
                ds.setUnderlineText(false);
                ds.setColor(getColor(R.color.hg_primary));
                ds.setFakeBoldText(true);
            }
        }, start, start + 8, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        registerTxt.setText(ss);
        registerTxt.setMovementMethod(LinkMovementMethod.getInstance());
        registerTxt.setHighlightColor(Color.TRANSPARENT);
    }

    private void forgotPassword() {
        String email = getText(emailField);
        if (email.isEmpty()) {
            emailLayout.setError("Enter your email first");
            loginBtn.performHapticFeedback(HapticFeedbackConstants.REJECT);
            return;
        }

        auth.sendPasswordResetEmail(email)
                .addOnSuccessListener(v ->
                        Toast.makeText(this, "Reset email sent", Toast.LENGTH_LONG).show()
                );
    }

    private TextWatcher simpleWatcher(Runnable r) {
        return new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            public void onTextChanged(CharSequence s, int a, int b, int c) { r.run(); }
            public void afterTextChanged(Editable s) {}
        };
    }

    private String getText(TextInputEditText et) {
        return et.getText() == null ? "" : et.getText().toString().trim();
    }
}
