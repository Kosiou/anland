package com.anland.consumer;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.util.Log;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Temporarily disables Samsung's system S Pen hover pointer while a desktop is foreground. */
final class StylusHoverPointerLease {
    private static final String TAG = "AnlandSpenPointer";
    private static final String SETTING = "pen_hovering_pointer";
    private static final String ACTIVE_PREF = "spen_pointer_override_active";
    private static final String ORIGINAL_PREF = "spen_pointer_override_original";
    private static final int UID_PER_USER_RANGE = 100000;
    private static final long COMMAND_TIMEOUT_MS = 5000;
    private static final long RELEASE_DELAY_MS = 700;

    private static final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "spen-pointer-lease");
        thread.setDaemon(true);
        return thread;
    });
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());
    private static StylusHoverPointerLease instance;

    private final SharedPreferences prefs;
    private final SuCommand.CommandRunner runner = new SuCommand.SuRunner();
    private final Set<Object> owners = Collections.newSetFromMap(new IdentityHashMap<>());
    private boolean applied;

    static synchronized StylusHoverPointerLease get(Context context) {
        if (instance == null)
            instance = new StylusHoverPointerLease(context.getApplicationContext());
        return instance;
    }

    private StylusHoverPointerLease(Context context) {
        prefs = context.getSharedPreferences("anland_settings", Context.MODE_PRIVATE);
    }

    void acquire(Object owner) {
        synchronized (this) {
            if (!owners.add(owner))
                return;
            mainHandler.removeCallbacks(releaseRunnable);
        }
        executor.execute(this::acquireOnWorker);
    }

    void recoverStaleOverride() {
        executor.execute(() -> {
            synchronized (StylusHoverPointerLease.this) {
                if (applied)
                    return;
            }
            if (prefs.getBoolean(ACTIVE_PREF, false))
                restoreOverride();
        });
    }

    void release(Object owner) {
        synchronized (this) {
            owners.remove(owner);
            if (!owners.isEmpty())
                return;
            mainHandler.removeCallbacks(releaseRunnable);
            mainHandler.postDelayed(releaseRunnable, RELEASE_DELAY_MS);
        }
    }

    private final Runnable releaseRunnable = () -> executor.execute(() -> {
        synchronized (StylusHoverPointerLease.this) {
            if (!owners.isEmpty())
                return;
        }
        restoreOverride();
    });

    private void acquireOnWorker() {
        synchronized (this) {
            if (owners.isEmpty())
                return;
            if (applied)
                return;
        }

        if (prefs.getBoolean(ACTIVE_PREF, false) && !restoreOverride())
            return;

        synchronized (this) {
            if (owners.isEmpty())
                return;
        }

        SettingValue current = readSetting();
        if (!current.valid) {
            Log.w(TAG, "Cannot read system S Pen hover pointer setting");
            return;
        }

        if (!prefs.edit().putString(ORIGINAL_PREF, current.value)
                .putBoolean(ACTIVE_PREF, true).commit()) {
            Log.e(TAG, "Cannot persist original S Pen pointer setting; leaving it unchanged");
            return;
        }

        if (!"0".equals(current.value)) {
            SuCommand.CommandResult result = run(putCommand(currentUserId(), "0"));
            if (!result.isClean()) {
                SettingValue after = readSetting();
                if (!after.valid || !"0".equals(after.value)) {
                    clearSavedOverride();
                    Log.w(TAG, "Root could not disable the S Pen hover pointer: "
                            + result.stderr.trim());
                    return;
                }
            }
        }

        synchronized (this) {
            applied = true;
        }
        Log.i(TAG, "Temporarily disabled the system S Pen hover pointer");
    }

    private boolean restoreOverride() {
        if (!prefs.getBoolean(ACTIVE_PREF, false)) {
            synchronized (this) {
                applied = false;
            }
            return true;
        }

        String original = prefs.getString(ORIGINAL_PREF, null);
        if (!isSettingValue(original)) {
            Log.e(TAG, "Saved S Pen pointer setting is invalid; keeping recovery state");
            return false;
        }

        int userId = currentUserId();
        SettingValue current = readSetting();
        if (!current.valid) {
            Log.w(TAG, "Cannot read S Pen pointer setting for restoration");
            return false;
        }

        String command = restoreCommand(userId, current.value, original);
        if (command != null) {
            SuCommand.CommandResult result = run(command);
            if (!result.isClean()) {
                Log.w(TAG, "Root could not restore S Pen pointer setting: "
                        + result.stderr.trim());
                return false;
            }
            SettingValue restored = readSetting();
            if (!restored.valid || !original.equals(restored.value)) {
                Log.w(TAG, "S Pen pointer setting did not return to its original value");
                return false;
            }
            Log.i(TAG, "Restored the original system S Pen hover pointer setting");
        } else {
            Log.i(TAG, "S Pen hover pointer setting changed during the session; preserving it");
        }

        clearSavedOverride();
        synchronized (this) {
            applied = false;
        }
        return true;
    }

    private SettingValue readSetting() {
        SuCommand.CommandResult result = run(getCommand(currentUserId()));
        if (!result.isClean())
            return new SettingValue(false, null);
        String value = result.stdout.trim();
        return new SettingValue(isSettingValue(value), value);
    }

    private SuCommand.CommandResult run(String command) {
        return runner.run(command, COMMAND_TIMEOUT_MS);
    }

    private static int currentUserId() {
        return Process.myUid() / UID_PER_USER_RANGE;
    }

    private void clearSavedOverride() {
        prefs.edit().remove(ACTIVE_PREF).remove(ORIGINAL_PREF).commit();
    }

    static String getCommand(int userId) {
        return "settings --user " + userId + " get system " + SETTING;
    }

    static String putCommand(int userId, String value) {
        if (!"0".equals(value) && !"1".equals(value))
            throw new IllegalArgumentException("unsupported setting value");
        return "settings --user " + userId + " put system " + SETTING + " " + value;
    }

    static String restoreCommand(int userId, String current, String original) {
        if (!"0".equals(current) || !isSettingValue(original))
            return null;
        if ("null".equals(original))
            return "settings --user " + userId + " delete system " + SETTING;
        return putCommand(userId, original);
    }

    private static boolean isSettingValue(String value) {
        return "0".equals(value) || "1".equals(value) || "null".equals(value);
    }

    private static final class SettingValue {
        final boolean valid;
        final String value;

        SettingValue(boolean valid, String value) {
            this.valid = valid;
            this.value = value;
        }
    }
}
