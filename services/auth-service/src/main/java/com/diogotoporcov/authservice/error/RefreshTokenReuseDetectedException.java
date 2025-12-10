package com.diogotoporcov.authservice.error;

public class RefreshTokenReuseDetectedException extends RuntimeException {
    public RefreshTokenReuseDetectedException() {
        super("Refresh token reuse detected");
    }
}
