package com.jnetai.appstore.net;

import com.jnetai.appstore.util.ErrorHandler;

/**
 * Typed exception carrying a stable error code for the store subsystem.
 */
public class StoreException extends Exception {

    private final String errorCode;

    public StoreException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public StoreException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
