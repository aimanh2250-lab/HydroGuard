package com.example.hydroguard;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.graphics.Color;
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
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;

public class RegisterActivity extends AppCompatActivity {

    private TextInputLayout emailLayout, passwordLayout, confirmLayout;
    private TextInputEditText emailInput, passwordInput, confirmPasswordInput;

    private MaterialButton registerButton;
    private View registerLoading;
    private TextView loginText;
    private TextView tvPasswordStrength;

    private View passwordRules;
    private TextView ruleLength, ruleUpper, ruleLower, ruleNumber, ruleSymbol;

    private FirebaseAuth auth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        bindViews();
        auth = FirebaseAuth.getInstance();

        playEntranceAnimation();
        makeOnlyLoginClickable();
        attachFieldWatchers();

        registerButton.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            createAccount();
        });
    }

    private void createAccount() {
        clearFieldErrors();

        String email = getText(emailInput);
        String pass = getText(passwordInput);
        String confirm = getText(confirmPasswordInput);

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
        } else if (!isPasswordStrong(pass)) {
            passwordLayout.setError(
                    "At least 8 chars, uppercase, lowercase, number & symbol"
            );
            ok = false;
        }

        if (confirm.isEmpty()) {
            confirmLayout.setError("Please confirm your password");
            ok = false;
        } else if (!confirm.equals(pass)) {
            confirmLayout.setError("Passwords do not match");
            ok = false;
        }

        if (!ok) {
            registerButton.performHapticFeedback(HapticFeedbackConstants.REJECT);
            return;
        }

        setLoading(true);

        auth.createUserWithEmailAndPassword(email, pass)
                .addOnSuccessListener(result -> {
                    setLoading(false);
                    registerButton.performHapticFeedback(HapticFeedbackConstants.CONFIRM);

                    Toast.makeText(
                            this,
                            "Account created successfully!",
                            Toast.LENGTH_SHORT
                    ).show();

                    startActivity(new Intent(this, LoginActivity.class));
                    finish();
                })
                .addOnFailureListener(e -> {
                    setLoading(false);
                    registerButton.performHapticFeedback(HapticFeedbackConstants.REJECT);
                    Toast.makeText(this, e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private boolean isPasswordStrong(String password) {
        return password.length() >= 8
                && password.matches(".*[A-Z].*")
                && password.matches(".*[a-z].*")
                && password.matches(".*\\d.*")
                && password.matches(".*[!@#$%^&*+=?-].*");
    }

    private void updatePasswordStrength(String password) {
        if (password.isEmpty()) {
            tvPasswordStrength.setVisibility(View.GONE);
            passwordRules.setVisibility(View.GONE);
            return;
        }

        tvPasswordStrength.setVisibility(View.VISIBLE);
        passwordRules.setVisibility(View.VISIBLE);

        boolean hasLength = password.length() >= 8;
        boolean hasUpper = password.matches(".*[A-Z].*");
        boolean hasLower = password.matches(".*[a-z].*");
        boolean hasNumber = password.matches(".*\\d.*");
        boolean hasSymbol = password.matches(".*[!@#$%^&*+=?-].*");

        updateRule(ruleLength, hasLength);
        updateRule(ruleUpper, hasUpper);
        updateRule(ruleLower, hasLower);
        updateRule(ruleNumber, hasNumber);
        updateRule(ruleSymbol, hasSymbol);

        int score = 0;
        if (hasLength) score++;
        if (hasUpper) score++;
        if (hasLower) score++;
        if (hasNumber) score++;
        if (hasSymbol) score++;

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

    private void updateRule(TextView rule, boolean passed) {
        rule.setText(
                (passed ? "✓ " : "• ")
                        + rule.getText().toString()
                        .replace("✓ ", "")
                        .replace("• ", "")
        );
        rule.setTextColor(
                passed
                        ? Color.parseColor("#22C55E")
                        : Color.parseColor("#9CA3AF")
        );
    }

    private void bindViews() {
        emailLayout = findViewById(R.id.emailLayout);
        passwordLayout = findViewById(R.id.passwordLayout);
        confirmLayout = findViewById(R.id.confirmLayout);

        emailInput = findViewById(R.id.emailInput);
        passwordInput = findViewById(R.id.passwordInput);
        confirmPasswordInput = findViewById(R.id.confirmPasswordInput);

        registerButton = findViewById(R.id.registerButton);
        registerLoading = findViewById(R.id.registerLoading);
        loginText = findViewById(R.id.loginText);

        tvPasswordStrength = findViewById(R.id.tvPasswordStrength);
        passwordRules = findViewById(R.id.passwordRules);
        ruleLength = findViewById(R.id.ruleLength);
        ruleUpper = findViewById(R.id.ruleUpper);
        ruleLower = findViewById(R.id.ruleLower);
        ruleNumber = findViewById(R.id.ruleNumber);
        ruleSymbol = findViewById(R.id.ruleSymbol);

        registerButton.setEnabled(false);
        registerButton.setAlpha(0.6f);
    }

    private void setLoading(boolean loading) {
        registerButton.setEnabled(!loading);
        registerButton.setText(loading ? "Creating…" : "Register");
        registerButton.setAlpha(loading ? 0.9f : 1f);
        registerLoading.setVisibility(loading ? View.VISIBLE : View.GONE);

        emailLayout.setEnabled(!loading);
        passwordLayout.setEnabled(!loading);
        confirmLayout.setEnabled(!loading);
    }

    private void clearFieldErrors() {
        emailLayout.setError(null);
        passwordLayout.setError(null);
        confirmLayout.setError(null);
    }

    private void attachFieldWatchers() {

        emailInput.addTextChangedListener(simpleWatcher(() ->
                emailLayout.setError(null)
        ));

        passwordInput.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            public void afterTextChanged(Editable s) {}

            public void onTextChanged(CharSequence s, int start, int before, int count) {
                passwordLayout.setError(null);
                updatePasswordStrength(s.toString());
                validateRegisterButton();
            }
        });

        confirmPasswordInput.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            public void afterTextChanged(Editable s) {}

            public void onTextChanged(CharSequence s, int start, int before, int count) {
                confirmLayout.setError(null);
                validateRegisterButton();
            }
        });
    }

    private void validateRegisterButton() {
        boolean strong = isPasswordStrong(getText(passwordInput));
        boolean match = getText(passwordInput).equals(getText(confirmPasswordInput))
                && !getText(confirmPasswordInput).isEmpty();

        if (strong && match) {
            registerButton.setEnabled(true);
            registerButton.setAlpha(1f);
        } else {
            registerButton.setEnabled(false);
            registerButton.setAlpha(0.6f);
        }
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

    private void playEntranceAnimation() {
        Animation anim = AnimationUtils.loadAnimation(this, R.anim.slide_fade_in);

        findViewById(R.id.appIconRegister).startAnimation(anim);
        findViewById(R.id.registerCard).startAnimation(anim);
        registerButton.startAnimation(anim);
        loginText.startAnimation(anim);
    }

    private void makeOnlyLoginClickable() {
        SpannableString ss =
                new SpannableString("Already have an account? Login");

        int start = ss.toString().indexOf("Login");

        ss.setSpan(new ClickableSpan() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(RegisterActivity.this, LoginActivity.class));
                finish();
            }

            @Override
            public void updateDrawState(TextPaint ds) {
                ds.setUnderlineText(false);
                ds.setFakeBoldText(true);
                ds.setColor(getColor(R.color.hg_primary));
            }
        }, start, start + 5, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        loginText.setText(ss);
        loginText.setMovementMethod(LinkMovementMethod.getInstance());
        loginText.setHighlightColor(Color.TRANSPARENT);
    }
}
