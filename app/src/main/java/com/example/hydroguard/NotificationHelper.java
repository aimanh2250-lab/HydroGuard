package com.example.hydroguard;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

public class NotificationHelper {

    private static final String CHANNEL_ID = "hydroguard_alerts";
    private static final String CHANNEL_NAME = "HydroGuard Local Alerts";
    private static final String CHANNEL_DESC = "Leak, limit, and system alerts from HydroGuard";

    private static final String PREF_NAME = "notify_guard";

    // ===================== DUPLICATE GUARD ===================== //

    public static boolean wasLeakAlreadyNotified(Context ctx, String deviceId) {
        if (ctx == null || deviceId == null) return false;

        SharedPreferences sp =
                ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        return sp.getBoolean("leak_" + deviceId, false);
    }

    public static void markLeakAsNotified(Context ctx, String deviceId) {
        if (ctx == null || deviceId == null) return;

        SharedPreferences sp =
                ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        sp.edit().putBoolean("leak_" + deviceId, true).apply();
    }

    public static void clearLeakNotified(Context ctx, String deviceId) {
        if (ctx == null || deviceId == null) return;

        SharedPreferences sp =
                ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        sp.edit().remove("leak_" + deviceId).apply();
    }

    // ===================== NOTIFICATION ===================== //

    public static void showLocalNotification(Context ctx, String title, String body) {
        if (ctx == null) return;

        // Android 13+ permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                return;
            }
        }

        NotificationManager manager =
                (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription(CHANNEL_DESC);
            manager.createNotificationChannel(channel);
        }

        int notificationId = (int) (System.currentTimeMillis() & 0x7FFFFFFF);

        Intent intent = new Intent(ctx, DashboardActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }

        PendingIntent pendingIntent =
                PendingIntent.getActivity(ctx, notificationId, intent, flags);

        NotificationCompat.Builder builder =
                new NotificationCompat.Builder(ctx, CHANNEL_ID)
                        .setSmallIcon(R.drawable.ic_notification)
                        .setContentTitle(title != null ? title : "HydroGuard")
                        .setContentText(body != null ? body : "")
                        .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                        .setAutoCancel(true)
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .setContentIntent(pendingIntent);

        manager.notify(notificationId, builder.build());
    }
}
