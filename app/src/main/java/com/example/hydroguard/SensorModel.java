package com.example.hydroguard;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Locale;

/**
 * HydroGuard SensorModel (Rule-aligned)
 *
 * Objective mapping (must follow everywhere):
 *  - L1 = SHOWER
 *  - L2 = TAP
 *
 * Firebase Live State Path:
 * /hydroguard/devices/{uid}/HG001/lines/{L1|L2}/state/
 *   flow (float), total (float), todayUsage (float),
 *   valve ("OPEN"|"CLOSED"),
 *   leak (bool),
 *   latched (bool),
 *   mode ("NORMAL"|"WARNING"|"SHUTOFF"),
 *   sourceName ("Shower"/"Tap"),
 *   sourceKey ("shower"/"tap")
 *
 * Note:
 * - UI must show only Shower/Tap, never L1/L2.
 * - L1/L2 used only for Firebase paths.
 */
public class SensorModel {

    // -------------------------
    // Internal identifiers
    // -------------------------
    /** "L1" or "L2" (used ONLY for Firebase path) */
    public String lineLabel;

    // -------------------------
    // Display labels (UI)
    // -------------------------
    /** Must be "Shower" for L1, "Tap" for L2 */
    public String sourceName;

    /** Must be "shower" for L1, "tap" for L2 */
    public String sourceKey;

    // -------------------------
    // Live state (matches Firebase)
    // -------------------------
    public float flow;        // L/min
    public float total;       // liters
    public float todayUsage;  // liters today
    public String valve;      // "OPEN" | "CLOSED"
    public boolean leak;
    public boolean latched;
    public String mode;       // "NORMAL" | "WARNING" | "SHUTOFF"

    // Optional UI metadata (safe to keep if your UI uses them)
    public String name;       // optional (e.g., "HG001 Shower")
    public String location;   // optional

    public SensorModel() {}

    // -------------------------
    // Factory: always enforce mapping
    // -------------------------
    @NonNull
    public static SensorModel createForLine(@NonNull String lineLabel) {
        SensorModel m = new SensorModel();
        m.applyLineMapping(lineLabel);
        m.flow = 0f;
        m.total = 0f;
        m.todayUsage = 0f;
        m.valve = "CLOSED";
        m.leak = false;
        m.latched = false;
        m.mode = "NORMAL";
        m.name = m.sourceName;
        m.location = "-";
        return m;
    }

    /**
     * Enforce objective mapping:
     * L1 -> Shower/shower
     * L2 -> Tap/tap
     */
    public void applyLineMapping(@NonNull String lineLabel) {
        this.lineLabel = safe(lineLabel);

        if ("L1".equalsIgnoreCase(this.lineLabel)) {
            this.sourceName = "Shower";
            this.sourceKey = "shower";
        } else {
            // Default everything else to L2 mapping (Tap)
            this.lineLabel = "L2";
            this.sourceName = "Tap";
            this.sourceKey = "tap";
        }
    }

    // -------------------------
    // Safe getters
    // -------------------------
    @NonNull
    public String getSafeLineLabel() {
        return safe(lineLabel);
    }

    /** UI label only (never L1/L2) */
    @NonNull
    public String getDisplayName() {
        return safe(sourceName).isEmpty() ? "Unknown" : safe(sourceName);
    }

    @NonNull
    public String getSourceKey() {
        return safe(sourceKey).isEmpty() ? defaultKeyFromLine() : safe(sourceKey);
    }

    @NonNull
    public String getValveSafe() {
        String v = safe(valve);
        if ("OPEN".equalsIgnoreCase(v)) return "OPEN";
        if ("CLOSED".equalsIgnoreCase(v)) return "CLOSED";
        return ""; // unknown/empty allowed briefly
    }

    @NonNull
    public String getModeSafe() {
        String m = safe(mode);
        if (m.isEmpty()) return "NORMAL";
        return m.toUpperCase(Locale.ROOT);
    }

    // -------------------------
    // Numeric formatting helpers
    // -------------------------
    public float getFlow() { return safeF(flow); }
    public float getTotal() { return safeF(total); }
    public float getTodayUsage() { return safeF(todayUsage); }

    @NonNull
    public String formatFlow() {
        return String.format(Locale.getDefault(), "%.2f L/min", getFlow());
    }

    @NonNull
    public String formatToday() {
        return String.format(Locale.getDefault(), "%.2f L", getTodayUsage());
    }

    @NonNull
    public String formatTotal() {
        return String.format(Locale.getDefault(), "%.2f L", getTotal());
    }

    // -------------------------
    // Valve helpers
    // -------------------------
    public boolean isValveOpen() {
        return "OPEN".equalsIgnoreCase(getValveSafe());
    }

    public boolean isValveClosed() {
        return "CLOSED".equalsIgnoreCase(getValveSafe());
    }

    /**
     * UI button rule priority:
     * 1) leak == true => disabled LEAK DETECTED
     * 2) latched == true => RESET VALVE
     * 3) valve == OPEN => CLOSE VALVE
     * 4) valve == CLOSED => OPEN VALVE
     */
    @NonNull
    public ValveUi computeValveUi() {
        if (leak) {
            return new ValveUi("NONE", "LEAK DETECTED", false, Tone.DISABLED);
        }
        if (latched) {
            return new ValveUi("RESET", "RESET VALVE", true, Tone.NEUTRAL);
        }
        if (isValveOpen()) {
            return new ValveUi("CLOSE", "CLOSE VALVE", true, Tone.DANGER);
        }
        // Treat unknown as CLOSED -> show OPEN (better UX)
        return new ValveUi("OPEN", "OPEN VALVE", true, Tone.SUCCESS);
    }

    public enum Tone { SUCCESS, DANGER, NEUTRAL, DISABLED }

    public static class ValveUi {
        public final String command;  // "OPEN"|"CLOSE"|"RESET"|"NONE"
        public final String text;
        public final boolean enabled;
        public final Tone tone;

        public ValveUi(String command, String text, boolean enabled, Tone tone) {
            this.command = command;
            this.text = text;
            this.enabled = enabled;
            this.tone = tone;
        }
    }

    // -------------------------
    // Internal utils
    // -------------------------
    @NonNull
    private static String safe(@Nullable String s) {
        return s == null ? "" : s.trim();
    }

    private static float safeF(float v) {
        if (Float.isNaN(v) || Float.isInfinite(v)) return 0f;
        return Math.max(0f, v);
    }

    @NonNull
    private String defaultKeyFromLine() {
        return "L1".equalsIgnoreCase(getSafeLineLabel()) ? "shower" : "tap";
    }
}
