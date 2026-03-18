package com.example.hydroguard;

public class PaymentModel {
    public String receiptId;
    public String method;
    public String month;
    public String usage;
    public float amount;
    public String status;
    public String timestamp; // epoch millis as String

    public PaymentModel() {}

    public PaymentModel(String receiptId, String method, String month, String usage,
                        float amount, String status, String timestamp) {
        this.receiptId = receiptId;
        this.method = method;
        this.month = month;
        this.usage = usage;
        this.amount = amount;
        this.status = status;
        this.timestamp = timestamp;
    }

    // ✅ ADD THIS
    public long getReceiptTime() {
        try {
            return Long.parseLong(timestamp);
        } catch (Exception e) {
            return 0L;
        }
    }

}
