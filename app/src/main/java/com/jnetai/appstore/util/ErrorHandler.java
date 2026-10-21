package com.jnetai.appstore.util;

import android.app.Activity;
import android.content.Context;
import android.widget.Toast;

import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * Central permanent error handler for J~Net App Store.
 *
 * Every handled failure produces:
 *   - a stable error code (see the ERR-* constants / call sites),
 *   - detailed debug output (message + exception type + full stack trace),
 *   - user feedback where an Activity is available.
 *
 * Kept in the project permanently per PROMPT.md.
 */
public final class ErrorHandler {

    public static final class Code {
        public static final String NETWORK = "ERR-NET";
        public static final String PARSE = "ERR-PARSE";
        public static final String IO = "ERR-IO";
        public static final String INSTALL = "ERR-INSTALL";
        public static final String UPDATE = "ERR-UPDATE";
        public static final String ABOUT = "ERR-ABOUT";
        public static final String MAIN = "ERR-MAIN";
        public static final String ADAPTER = "ERR-ADAPTER";

        private Code() {
        }
    }

    private ErrorHandler() {
    }

    public static void handle(String tag, String errorCode, String message,
                              Throwable throwable, Context toastContext) {
        StringBuilder log = new StringBuilder();
        log.append("[").append(errorCode).append("] ").append(message);
        if (throwable != null) {
            log.append("\n  exception: ").append(throwable.getClass().getName());
            log.append("\n  detail: ").append(throwable.getMessage());
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            throwable.printStackTrace(pw);
            log.append("\n  stack:\n").append(sw);
        }
        DebugLogger.error(tag, log.toString(), throwable);

        if (toastContext != null) {
            final String shortMsg = "[" + errorCode + "] " + message;
            if (toastContext instanceof Activity) {
                Activity activity = (Activity) toastContext;
                activity.runOnUiThread(() ->
                        Toast.makeText(activity, shortMsg, Toast.LENGTH_LONG).show());
            }
        }
    }

    public static void handle(String tag, String errorCode, String message, Throwable throwable) {
        handle(tag, errorCode, message, throwable, null);
    }
}
