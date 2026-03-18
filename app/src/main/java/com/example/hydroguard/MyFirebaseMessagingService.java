package com.example.hydroguard;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ServerValue;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import java.util.Map;

public class MyFirebaseMessagingService extends FirebaseMessagingService {

    @Override
    public void onMessageReceived(@NonNull RemoteMessage message) {
        super.onMessageReceived(message);

        Map<String, String> data = message.getData();
        if (data == null || data.isEmpty()) return;

        String type = data.get("type");
        String deviceId = data.get("deviceId");
        String state = data.get("state");

        // 🔒 HARD FILTER
        if (!"LEAK".equals(type)) return;
        if (!"DETECTED".equals(state)) return;

        // 🔒 PREVENT DUPLICATES
        if (NotificationHelper.wasLeakAlreadyNotified(this, deviceId)) {
            return;
        }

        String title = "🚨 Leak Detected";
        String body = "Water leak detected at device " + deviceId;

        NotificationHelper.showLocalNotification(this, title, body);
        NotificationHelper.markLeakAsNotified(this, deviceId);
    }

    public static boolean wasLeakAlreadyNotified(Context ctx, String deviceId) {
        SharedPreferences sp = ctx.getSharedPreferences("notify_guard", MODE_PRIVATE);
        return sp.getBoolean("leak_" + deviceId, false);
    }

    public static void markLeakAsNotified(Context ctx, String deviceId) {
        SharedPreferences sp = ctx.getSharedPreferences("notify_guard", MODE_PRIVATE);
        sp.edit().putBoolean("leak_" + deviceId, true).apply();
    }

    @Override
    public void onNewToken(@NonNull String token) {
        super.onNewToken(token);
        Log.d("FCM", "New Token: " + token);

        String uid = FirebaseAuth.getInstance().getUid();
        if (uid != null && token != null && !token.trim().isEmpty()) {
            FirebaseDatabase.getInstance()
                    .getReference("hydroguard/userTokens")
                    .child(uid)
                    .child(token)
                    .setValue(ServerValue.TIMESTAMP);
        }
    }

}
