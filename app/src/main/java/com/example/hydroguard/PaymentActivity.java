package com.example.hydroguard;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class PaymentActivity extends AppCompatActivity {

    private MaterialToolbar toolbar;

    private TextView tvPayMonth, tvPayUsage, tvPayAmount;
    private RadioGroup rgMethod;
    private RadioButton rbFPX, rbCard, rbTng;

    private View sectionFPX, sectionCard, sectionTng;

    private EditText etBankName;
    private EditText etCardName, etCardNumber, etCardExpiry, etCardCvv;
    private EditText etTngPhone;

    private MaterialButton btnConfirmPay;

    private float amount = 0f;
    private String monthLabel = "";
    private String usageText = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_payment);

        toolbar = findViewById(R.id.toolbarPay);
        tvPayMonth = findViewById(R.id.tvPayMonth);
        tvPayUsage = findViewById(R.id.tvPayUsage);
        tvPayAmount = findViewById(R.id.tvPayAmount);

        rgMethod = findViewById(R.id.rgMethod);
        rbFPX = findViewById(R.id.rbFPX);
        rbCard = findViewById(R.id.rbCard);
        rbTng = findViewById(R.id.rbTng);

        sectionFPX = findViewById(R.id.sectionFPX);
        sectionCard = findViewById(R.id.sectionCard);
        sectionTng = findViewById(R.id.sectionTng);

        etBankName = findViewById(R.id.etBankName);

        etCardName = findViewById(R.id.etCardName);
        etCardNumber = findViewById(R.id.etCardNumber);
        etCardExpiry = findViewById(R.id.etCardExpiry);
        etCardCvv = findViewById(R.id.etCardCvv);

        etTngPhone = findViewById(R.id.etTngPhone);

        btnConfirmPay = findViewById(R.id.btnConfirmPay);

        // Toolbar back
        toolbar.setNavigationOnClickListener(v -> finish());

        // Get data from BillingActivity
        Intent i = getIntent();
        if (i != null) {
            monthLabel = i.getStringExtra("monthLabel");
            usageText = i.getStringExtra("usageText");
            amount = i.getFloatExtra("amount", 0f);
        }

        tvPayMonth.setText(monthLabel == null ? "-" : monthLabel);
        tvPayUsage.setText(usageText == null ? "-" : usageText);
        tvPayAmount.setText(String.format(Locale.getDefault(), "RM %.2f", amount));

        // Default = FPX
        rbFPX.setChecked(true);
        updateMethodSections();

        rgMethod.setOnCheckedChangeListener((group, checkedId) -> updateMethodSections());
        btnConfirmPay.setOnClickListener(v -> doDummyPayment());
    }

    private void updateMethodSections() {
        int id = rgMethod.getCheckedRadioButtonId();
        sectionFPX.setVisibility(id == R.id.rbFPX ? View.VISIBLE : View.GONE);
        sectionCard.setVisibility(id == R.id.rbCard ? View.VISIBLE : View.GONE);
        sectionTng.setVisibility(id == R.id.rbTng ? View.VISIBLE : View.GONE);
    }

    private void doDummyPayment() {
        if (amount <= 0f) {
            Toast.makeText(this, "Invalid amount.", Toast.LENGTH_SHORT).show();
            return;
        }

        int id = rgMethod.getCheckedRadioButtonId();
        String method;

        if (id == R.id.rbFPX) {
            method = "FPX";
            if (TextUtils.isEmpty(etBankName.getText().toString().trim())) {
                etBankName.setError("Enter bank name");
                etBankName.requestFocus();
                return;
            }
        } else if (id == R.id.rbCard) {
            method = "Card";
            if (!validateCard()) return;
        } else {
            method = "TNG eWallet";
            String phone = etTngPhone.getText().toString().trim();
            if (phone.length() < 9) {
                etTngPhone.setError("Enter valid phone");
                etTngPhone.requestFocus();
                return;
            }
        }

        // fake receipt
        String receiptId = "HG-" + System.currentTimeMillis();

        // ✅ Save to Firebase (so Billing can show Payment History & PAID status)
        savePaymentToFirebase(receiptId, method);

        Intent s = new Intent(PaymentActivity.this, PaymentSuccessActivity.class);
        s.putExtra("receiptId", receiptId);
        s.putExtra("method", method);
        s.putExtra("monthLabel", monthLabel);
        s.putExtra("usageText", usageText);
        s.putExtra("amount", amount);
        startActivity(s);
        finish();
    }

    private void savePaymentToFirebase(@NonNull String receiptId, @NonNull String method) {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) uid = "guest";

        DatabaseReference ref = FirebaseDatabase.getInstance()
                .getReference("hydroguard/payments")
                .child(uid)
                .child(receiptId);

        String time = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                .format(new Date());

        ref.child("receiptId").setValue(receiptId);
        ref.child("method").setValue(method);
        ref.child("month").setValue(monthLabel == null ? "" : monthLabel);
        ref.child("usage").setValue(usageText == null ? "" : usageText);
        ref.child("amount").setValue(amount);
        ref.child("status").setValue("SUCCESS");
        ref.child("timestamp").setValue(time);
    }

    private boolean validateCard() {
        String name = etCardName.getText().toString().trim();
        String num = etCardNumber.getText().toString().trim().replace(" ", "");
        String exp = etCardExpiry.getText().toString().trim();
        String cvv = etCardCvv.getText().toString().trim();

        if (name.length() < 3) {
            etCardName.setError("Enter name");
            etCardName.requestFocus();
            return false;
        }
        if (num.length() < 12) {
            etCardNumber.setError("Enter card number");
            etCardNumber.requestFocus();
            return false;
        }
        if (exp.length() < 4) {
            etCardExpiry.setError("MM/YY");
            etCardExpiry.requestFocus();
            return false;
        }
        if (cvv.length() < 3) {
            etCardCvv.setError("CVV");
            etCardCvv.requestFocus();
            return false;
        }
        return true;
    }
}
