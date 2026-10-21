package com.jnetai.appstore.util;

import android.util.Log;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Permanent debug logger for J~Net App Store.
 *
 * NOTE: This logging system is intentionally kept in the project permanently
 * (see PROMPT.md) so that any future crash, failure or unexpected state can be
 * traced. Do not remove.
 */
public final class DebugLogger {

    private static final String TAG = "JNetAppStore";
    private static final SimpleDateFormat SDF =
            new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US);
    private static volatile boolean enabled = true;

    private DebugLogger() {
    }

    public static void log(String tag, String message) {
        if (enabled) {
            Log.d(TAG, "[" + timestamp() + "][" + tag + "] " + message);
        }
    }

    public static void warn(String tag, String message) {
        Log.w(TAG, "[" + timestamp() + "][" + tag + "] " + message);
    }

    public static void error(String tag, String message, Throwable t) {
        StringBuilder sb = new StringBuilder();
        sb.append("[").append(timestamp()).append("][").append(tag).append("] ").append(message);
        if (t != null) {
            sb.append("\n  exception: ").append(t.getClass().getName());
            sb.append("\n  detail: ").append(t.getMessage());
        }
        Log.e(TAG, sb.toString(), t);
    }

    public static String timestamp() {
        return SDF.format(new Date());
    }

    public static void setEnabled(boolean value) {
        enabled = value;
    }

    public static boolean isEnabled() {
        return enabled;
    }
}
