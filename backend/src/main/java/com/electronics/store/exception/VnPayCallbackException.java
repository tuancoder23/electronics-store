package com.electronics.store.exception;

import lombok.Getter;

@Getter
public class VnPayCallbackException extends RuntimeException {
    private final String responseCode;

    public VnPayCallbackException(String responseCode, String message) {
        super(message);
        this.responseCode = responseCode;
    }
}
