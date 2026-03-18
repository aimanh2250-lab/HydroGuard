package com.example.hydroguard;

import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.util.Patterns;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;


import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.EmailAuthProvider;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.UserProfileChangeRequest;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ServerValue;

import java.util.HashMap;

public class EditProfileActivity extends AppCompatActivity {

    private EditText etName, etPhone;
    private MaterialButton btnSave, btnChangePassword, btnChangeEmail;

    private FirebaseAuth auth;
    private FirebaseDatabase db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_profile);

        auth = FirebaseAuth.getInstance();
        db = FirebaseDatabase.getInstance();

        // Toolbar (Back)
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationIcon(R.drawable.ic_back);
        toolbar.setNavigationOnClickListener(v -> finish());

        // Views
        etName = findViewById(R.id.etName);
        etPhone = findViewById(R.id.etPhone);
        btnSave = findViewById(R.id.btnSaveProfile);
        btnChangeEmail = findViewById(R.id.btnChangeEmail);
        btnChangePassword = findViewById(R.id.btnChangePassword);

        loadProfile();

        btnSave.setOnClickListener(v -> saveProfile());
        btnChangePassword.setOnClickListener(v -> showChangePasswordDialog());
        btnChangeEmail.setOnClickListener(v -> showChangeEmailDialog());
    }

    private void loadProfile() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            Toast.makeText(this, "No user logged in", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Prefill from Auth display name (instant)
        if (!TextUtils.isEmpty(user.getDisplayName())) {
            etName.setText(user.getDisplayName());
        }

        // Prefill from RTDB (source of truth for profile fields)
        String uid = user.getUid();
        db.getReference("hydroguard/users/" + uid + "/profile")
                .get()
                .addOnSuccessListener(snapshot -> {
                    if (!snapshot.exists()) return;

                    Object nameV = snapshot.child("name").getValue();
                    Object phoneV = snapshot.child("phone").getValue();

                    if (nameV != null) etName.setText(String.valueOf(nameV));
                    if (phoneV != null) etPhone.setText(String.valueOf(phoneV));
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, e.getMessage(), Toast.LENGTH_SHORT).show()
                );
    }

    private void saveProfile() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            Toast.makeText(this, "No user logged in", Toast.LENGTH_SHORT).show();
            return;
        }

        String name = etName.getText().toString().trim();
        String phone = etPhone.getText().toString().trim();

        if (TextUtils.isEmpty(name)) {
            etName.setError("Name is required");
            etName.requestFocus();
            return;
        }

        if (!TextUtils.isEmpty(phone) && phone.length() < 8) {
            etPhone.setError("Phone looks too short");
            etPhone.requestFocus();
            return;
        }

        setButtonsEnabled(false);

        String uid = user.getUid();

        HashMap<String, Object> map = new HashMap<>();
        map.put("name", name);
        map.put("phone", phone);
        map.put("updatedAt", ServerValue.TIMESTAMP);

        // 1) Save to RTDB
        db.getReference("hydroguard/users/" + uid + "/profile")
                .updateChildren(map)
                .addOnSuccessListener(unused -> {
                    // 2) Update FirebaseAuth displayName too (so Settings always shows it)
                    UserProfileChangeRequest req = new UserProfileChangeRequest.Builder()
                            .setDisplayName(name)
                            .build();

                    user.updateProfile(req)
                            .addOnSuccessListener(unused2 -> {
                                setButtonsEnabled(true);
                                Toast.makeText(this, "Profile updated", Toast.LENGTH_SHORT).show();
                                // ✅ DO NOT finish here
                            })
                            .addOnFailureListener(e -> {
                                setButtonsEnabled(true);
                                Toast.makeText(this,
                                        "Saved, but name sync failed: " + e.getMessage(),
                                        Toast.LENGTH_SHORT).show();
                                // ✅ DO NOT finish here
                            });
                })
                .addOnFailureListener(e -> {
                    setButtonsEnabled(true);
                    Toast.makeText(this, e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    // ================== CHANGE PASSWORD ================== //
    private void showChangePasswordDialog() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            Toast.makeText(this, "No user logged in", Toast.LENGTH_SHORT).show();
            return;
        }

        String email = user.getEmail();
        if (TextUtils.isEmpty(email)) {
            Toast.makeText(this, "No email found for this account", Toast.LENGTH_SHORT).show();
            return;
        }

        new MaterialAlertDialogBuilder(this)
                .setTitle("Change Password")
                .setMessage("We will send a password reset link to:\n\n" + email)
                .setNegativeButton("Cancel", (d, w) -> d.dismiss())
                .setPositiveButton("Send Link", (d, w) -> {
                    auth.sendPasswordResetEmail(email)
                            .addOnSuccessListener(unused ->
                                    Toast.makeText(this, "Reset link sent to email", Toast.LENGTH_SHORT).show()
                            )
                            .addOnFailureListener(e ->
                                    Toast.makeText(this, e.getMessage(), Toast.LENGTH_SHORT).show()
                            );
                })
                .show();
    }

    private boolean canChangeEmail(FirebaseUser user) {
        for (var info : user.getProviderData()) {
            if ("password".equals(info.getProviderId())) {
                return true;
            }
        }
        return false;
    }

    private void showChangeEmailDialog() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            Toast.makeText(this, "No user logged in", Toast.LENGTH_SHORT).show();
            return;
        }

        // 🔐 Check provider: only EMAIL/PASSWORD users can change email
        boolean canChangeEmail = false;
        for (var info : user.getProviderData()) {
            if ("password".equals(info.getProviderId())) {
                canChangeEmail = true;
                break;
            }
        }

        if (!canChangeEmail) {
            Toast.makeText(
                    this,
                    "Email cannot be changed for your login method.",
                    Toast.LENGTH_LONG
            ).show();
            return;
        }

        // ================= UI ================= //

        // TextInputLayout
        TextInputLayout til = new TextInputLayout(this);
        til.setHintEnabled(false);
        til.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);
        til.setBoxCornerRadii(14f, 14f, 14f, 14f);
        til.setBoxStrokeColor(
                ContextCompat.getColor(this, R.color.hg_outline)
        );

        // Input
        TextInputEditText input = new TextInputEditText(this);
        input.setHint("Enter new email");
        input.setInputType(InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        input.setSingleLine(true);
        input.setTextSize(15f);

        String currentEmail = user.getEmail();
        if (!TextUtils.isEmpty(currentEmail)) {
            input.setText(currentEmail);
            input.setSelection(currentEmail.length());
        }

        til.addView(input, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        // Container
        FrameLayout container = new FrameLayout(this);
        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        container.setPadding(pad, pad, pad, 0);
        container.addView(til);

        MaterialAlertDialogBuilder builder =
                new MaterialAlertDialogBuilder(this)
                        .setTitle("Change Email")
                        .setMessage("Enter your new email address.")
                        .setView(container)
                        .setNegativeButton("Cancel", null)
                        .setPositiveButton("Update", null);

        AlertDialog dialog = builder.create();
        dialog.show();

        // Safety check
        if (dialog.getButton(AlertDialog.BUTTON_POSITIVE) != null) {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                    .setOnClickListener(v -> {
                        String newEmail = input.getText() == null
                                ? ""
                                : input.getText().toString().trim();

                        if (TextUtils.isEmpty(newEmail)
                                || !Patterns.EMAIL_ADDRESS.matcher(newEmail).matches()) {
                            til.setError("Enter a valid email");
                            return;
                        }

                        til.setError(null);
                        dialog.dismiss();
                        attemptUpdateEmail(newEmail);
                    });
        }
    }

    private void attemptUpdateEmail(String newEmail) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) return;

        if (TextUtils.isEmpty(newEmail) || !Patterns.EMAIL_ADDRESS.matcher(newEmail).matches()) {
            Toast.makeText(this, "Please enter a valid email", Toast.LENGTH_SHORT).show();
            return;
        }

        setButtonsEnabled(false);

        user.updateEmail(newEmail)
                .addOnSuccessListener(unused -> {
                    // Optional but recommended: verify the new email
                    user.sendEmailVerification();

                    user.reload().addOnCompleteListener(t -> {
                        setButtonsEnabled(true);
                        Toast.makeText(this, "Email updated. Please verify your new email.", Toast.LENGTH_LONG).show();
                    });
                })
                .addOnFailureListener(e -> {
                    setButtonsEnabled(true);

                    if (e instanceof FirebaseAuthRecentLoginRequiredException) {
                        showReauthDialogAndRetry(newEmail);
                    } else {
                        Toast.makeText(this, e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void showReauthDialogAndRetry(String pendingNewEmail) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) return;

        String email = user.getEmail();
        if (TextUtils.isEmpty(email)) {
            Toast.makeText(this, "No email found for re-auth", Toast.LENGTH_SHORT).show();
            return;
        }

        TextInputLayout til = new TextInputLayout(this);
        til.setHint("Current password");

        TextInputEditText passInput = new TextInputEditText(this);
        passInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        passInput.setSingleLine(true);

        til.addView(passInput, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        FrameLayout container = new FrameLayout(this);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        container.setPadding(pad, pad / 2, pad, 0);
        container.addView(til);

        new MaterialAlertDialogBuilder(this)
                .setTitle("Re-login required")
                .setMessage("For security, please enter your current password to continue.")
                .setView(container)
                .setNegativeButton("Cancel", (d, w) -> d.dismiss())
                .setPositiveButton("Continue", (d, w) -> {
                    String password = passInput.getText() != null
                            ? passInput.getText().toString()
                            : "";

                    if (TextUtils.isEmpty(password)) {
                        Toast.makeText(this, "Password required", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    reauthThenRetryEmailUpdate(email, password, pendingNewEmail);
                })
                .show();
    }

    private void reauthThenRetryEmailUpdate(String email, String password, String pendingNewEmail) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) return;

        setButtonsEnabled(false);

        user.reauthenticate(EmailAuthProvider.getCredential(email, password))
                .addOnSuccessListener(unused -> {
                    user.updateEmail(pendingNewEmail)
                            .addOnSuccessListener(unused2 -> {
                                user.sendEmailVerification();

                                user.reload().addOnCompleteListener(t -> {
                                    setButtonsEnabled(true);
                                    Toast.makeText(this, "Email updated. Please verify your new email.", Toast.LENGTH_LONG).show();
                                });
                            })
                            .addOnFailureListener(e -> {
                                setButtonsEnabled(true);
                                Toast.makeText(this, e.getMessage(), Toast.LENGTH_SHORT).show();
                            });
                })
                .addOnFailureListener(e -> {
                    setButtonsEnabled(true);
                    Toast.makeText(this, "Re-auth failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private void setButtonsEnabled(boolean enabled) {
        btnSave.setEnabled(enabled);
        btnChangeEmail.setEnabled(enabled);
        btnChangePassword.setEnabled(enabled);
    }
}
