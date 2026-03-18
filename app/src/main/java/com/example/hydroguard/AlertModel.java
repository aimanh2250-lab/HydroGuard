package com.example.hydroguard;

/**
 * Option A Alert Model (must match Firebase schema exactly)
 *
 * /hydroguard/alerts/{uid}/{YYYY-MM-DD}/{pushId}/
 *   title, details, datetime, createdAt,
 *   tag ("LEAK"|"LIMIT"|"SYSTEM"),
 *   severity ("GREEN"|"ORANGE"|"RED"|"GRAY"),
 *   lineId ("L1"|"L2"),
 *   deviceId ("HG001"),
 *   source ("shower"|"tap")
 *
 * NOTE:
 * - UI must never show L1/L2, but we keep lineId for mapping.
 * - pushId + dayKey are local-only helpers (NOT written by DB unless you do).
 */
public class AlertModel {

    // Required core fields
    public String title;
    public String details;
    public String datetime;
    public Long createdAt;

    // Option A fields
    public String tag;        // "LEAK"|"LIMIT"|"SYSTEM"
    public String severity;   // "GREEN"|"ORANGE"|"RED"|"GRAY"
    public String lineId;     // "L1"|"L2"
    public String deviceId;   // "HG001"
    public String source;     // "shower"|"tap"

    // Local-only helpers (not part of Option A schema requirements)
    public String pushId;     // Firebase push key
    public String dayKey;     // YYYY-MM-DD

    public AlertModel() {}
}
