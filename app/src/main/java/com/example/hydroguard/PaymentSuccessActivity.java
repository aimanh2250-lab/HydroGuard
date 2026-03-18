package com.example.hydroguard;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;

import java.util.Locale;

public class PaymentSuccessActivity extends AppCompatActivity {

    private MaterialToolbar toolbar;
    private TextView tvReceipt, tvMethod, tvMonth, tvUsage, tvAmount;
    private MaterialButton btnBackToBilling;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_payment_success);

        toolbar = findViewById(R.id.toolbarSuccess);
        tvReceipt = findViewById(R.id.tvReceipt);
        tvMethod = findViewById(R.id.tvMethod);
        tvMonth = findViewById(R.id.tvMonth);
        tvUsage = findViewById(R.id.tvUsage);
        tvAmount = findViewById(R.id.tvAmount);
        btnBackToBilling = findViewById(R.id.btnBackToBilling);

        toolbar.setNavigationOnClickListener(v -> finish());

        Intent i = getIntent();
        String receiptId = i.getStringExtra("receiptId");
        String method = i.getStringExtra("method");
        String monthLabel = i.getStringExtra("monthLabel");
        String usageText = i.getStringExtra("usageText");
        float amount = i.getFloatExtra("amount", 0f);

        tvReceipt.setText("Receipt: " + (receiptId == null ? "-" : receiptId));
        tvMethod.setText("Method: " + (method == null ? "-" : method));
        tvMonth.setText("Month: " + (monthLabel == null ? "-" : monthLabel));
        tvUsage.setText("Usage: " + (usageText == null ? "-" : usageText));
        tvAmount.setText(String.format(Locale.getDefault(), "Amount: RM %.2f", amount));

        btnBackToBilling.setOnClickListener(v -> {
            Intent b = new Intent(PaymentSuccessActivity.this, BillingActivity.class);
            b.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(b);
            finish();
        });
    }
}
