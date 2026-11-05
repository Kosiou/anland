package com.anland.consumer;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Temporarily disables Samsung's Android S Pen hover pointer while Anland is active. */
final class StylusHoverPointerLease {
    private static final String TAG = "AnlandSpenPointer";
    private static final String SETTING = "pen_hovering_pointer";
    private static final String ACTIVE = "spen_pointer_override_active";
    private static final String ORIGINAL = "spen_pointer_override_original";
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "spen-pointer-lease");
        thread.setDaemon(true);
        return thread;
    });
    private static StylusHoverPointerLease instance;

    private final SharedPreferences prefs;
    private final Set<Object> owners = Collections.newSetFromMap(new IdentityHashMap<>());

    static synchronized StylusHoverPointerLease get(Context context) {
        if (instance == null)
            instance = new StylusHoverPointerLease(context.getApplicationContext());
        return instance;
    }

    private StylusHoverPointerLease(Context context) {
        prefs = context.getSharedPreferences("anland_settings", Context.MODE_PRIVATE);
    }

    void acquire(Object owner) {
        synchronized (owners) {
            if (!owners.add(owner))
                return;
        }
        WORKER.execute(this::applyIfOwned);
    }

    void release(Object owner) {
        synchronized (owners) {
            owners.remove(owner);
            if (!owners.isEmpty())
                return;
        }
        WORKER.execute(this::restoreIfUnowned);
    }

    void recoverStaleOverride() {
        WORKER.execute(() -> {
            synchronized (owners) {
                if (!owners.isEmpty())
                    return;
            }
            restore();
        });
    }

    private void applyIfOwned() {
        synchronized (owners) {
            if (owners.isEmpty())
                return;
        }
        if (!restore())
            return;
        SuCommand.CommandResult read = run(getCommand());
        String original = read.stdout.trim();
        if (!read.isClean() || !valid(original)) {
            Log.w(TAG, "Unable to read Android S Pen hover pointer setting");
            return;
        }
        if (!prefs.edit().putString(ORIGINAL, original).putBoolean(ACTIVE, true).commit())
            return;
        if (!"0".equals(original)) {
            SuCommand.CommandResult write = run(putCommand("0"));
            if (!write.isClean()) {
                prefs.edit().remove(ORIGINAL).remove(ACTIVE).commit();
                Log.w(TAG, "Root could not disable Android S Pen hover pointer: "
                        + write.stderr.trim());
                return;
            }
        }
        synchronized (owners) {
            if (!owners.isEmpty())
                return;
        }
        restoreIfUnowned();
    }

    private void restoreIfUnowned() {
        synchronized (owners) {
            if (!owners.isEmpty())
                return;
        }
        restore();
    }

    private boolean restore() {
        if (!prefs.getBoolean(ACTIVE, false))
            return true;
        String original = prefs.getString(ORIGINAL, null);
        if (!valid(original))
            return false;
        SuCommand.CommandResult read = run(getCommand());
        String current = read.stdout.trim();
        if (!read.isClean())
            return false;
        if ("0".equals(current)) {
            String command = "null".equals(original)
                    ? "settings --user " + userId() + " delete system " + SETTING
                    : putCommand(original);
            SuCommand.CommandResult write = run(command);
            if (!write.isClean()) {
                Log.w(TAG, "Root could not restore Android S Pen hover pointer: "
                        + write.stderr.trim());
                return false;
            }
        } else if (!original.equals(current)) {
            // Preserve a value another app or the user changed during our lease.
            Log.i(TAG, "Android S Pen pointer setting changed externally; preserving it");
        }
        prefs.edit().remove(ORIGINAL).remove(ACTIVE).commit();
        return true;
    }

    private SuCommand.CommandResult run(String command) {
        return new SuCommand.SuRunner().run(command, 5000);
    }

    private String getCommand() {
        return "settings --user " + userId() + " get system " + SETTING;
    }

    private String putCommand(String value) {
        return "settings --user " + userId() + " put system " + SETTING
                + " " + value;
    }

    private static int userId() {
        // Anland runs in the primary Android user on supported devices. Keeping
        // this literal avoids hidden UserHandle APIs unavailable to app builds.
        return 0;
    }

    private static boolean valid(String value) {
        return "0".equals(value) || "1".equals(value) || "null".equals(value);
    }
}
