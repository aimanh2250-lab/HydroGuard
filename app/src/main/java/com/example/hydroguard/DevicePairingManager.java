package com.example.hydroguard;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

public class DevicePairingManager {

    public interface PairCallback {
        void onPaired(String deviceId);
        void onAlreadyPairedByOther(String deviceId);
        void onError(String deviceId, String msg);
    }

    public interface OwnerCallback {
        void onResult(@Nullable String ownerUid);
        void onError(String msg);
    }

    private static @Nullable String uidOrNull() {
        return FirebaseAuth.getInstance().getUid();
    }

    private static DatabaseReference ownerRef(@NonNull String deviceId) {
        return FirebaseDatabase.getInstance()
                .getReference("hydroguard")
                .child("deviceOwners")
                .child(deviceId)
                .child("ownerUid");
    }

    private static DatabaseReference userDeviceRegistryRef(@NonNull String uid, @NonNull String deviceId) {
        return FirebaseDatabase.getInstance()
                .getReference("hydroguard")
                .child("users")
                .child(uid)
                .child("devices")
                .child(deviceId);
    }

    /**
     * Auto-pair behavior:
     * - If ownerUid empty: claim it (set to my uid)
     * - If ownerUid == my uid: already paired, still success
     * - Else: paired by someone else
     */
    public static void autoPairDevice(@NonNull String deviceId, @NonNull PairCallback cb) {
        String uid = uidOrNull();
        if (uid == null) {
            cb.onError(deviceId, "Not logged in");
            return;
        }

        DatabaseReference ref = ownerRef(deviceId);

        ref.runTransaction(new com.google.firebase.database.Transaction.Handler() {
            @NonNull
            @Override
            public com.google.firebase.database.Transaction.Result doTransaction(
                    @NonNull com.google.firebase.database.MutableData currentData) {

                String currentOwner = currentData.getValue(String.class);
                if (currentOwner != null) currentOwner = currentOwner.trim();

                // Unowned → claim
                if (currentOwner == null || currentOwner.isEmpty()) {
                    currentData.setValue(uid);
                    return com.google.firebase.database.Transaction.success(currentData);
                }

                // Already mine → allow
                if (uid.equals(currentOwner)) {
                    return com.google.firebase.database.Transaction.success(currentData);
                }

                // Owned by someone else → abort
                return com.google.firebase.database.Transaction.abort();
            }

            @Override
            public void onComplete(
                    @Nullable DatabaseError error,
                    boolean committed,
                    @Nullable DataSnapshot snapshot) {

                if (error != null) {
                    cb.onError(deviceId, error.getMessage());
                    return;
                }

                if (!committed) {
                    cb.onAlreadyPairedByOther(deviceId);
                    return;
                }

                // ✅ Save registry after confirmed ownership
                userDeviceRegistryRef(uid, deviceId).setValue(true);
                cb.onPaired(deviceId);
            }
        });
    }


    /**
     * Get current ownerUid for a device (for debugging or UI).
     */
    public static void getOwnerUid(@NonNull String deviceId, @NonNull OwnerCallback cb) {
        ownerRef(deviceId).get()
                .addOnSuccessListener(snapshot -> cb.onResult(snapshot.getValue(String.class)))
                .addOnFailureListener(e -> cb.onError(safeMsg(e)));
    }

    /**
     * Optional: release device (unpair).
     * Only works if your rules allow the owner to clear it.
     *
     * This will set ownerUid = "" AND remove from user registry.
     */
    public static void releaseDevice(@NonNull String deviceId, @NonNull PairCallback cb) {
        String uid = uidOrNull();
        if (uid == null) {
            cb.onError(deviceId, "Not logged in");
            return;
        }

        DatabaseReference ref = ownerRef(deviceId);
        ref.get().addOnSuccessListener(snapshot -> {
            String currentOwner = snapshot.getValue(String.class);
            if (currentOwner != null) currentOwner = currentOwner.trim();

            if (currentOwner == null || currentOwner.isEmpty()) {
                cb.onPaired(deviceId); // nothing to release
                return;
            }

            if (!uid.equals(currentOwner)) {
                cb.onAlreadyPairedByOther(deviceId);
                return;
            }

            // ✅ clear owner
            ref.setValue("")
                    .addOnSuccessListener(unused -> {
                        // ✅ remove from registry
                        userDeviceRegistryRef(uid, deviceId).removeValue();
                        cb.onPaired(deviceId);
                    })
                    .addOnFailureListener(e -> cb.onError(deviceId, safeMsg(e)));

        }).addOnFailureListener(e -> cb.onError(deviceId, safeMsg(e)));
    }

    private static String safeMsg(Exception e) {
        return e != null && e.getMessage() != null ? e.getMessage() : "Unknown error";
    }
}
