package com.fern.posservice.config;

public class PosOutboxProperties {
    private int maxAttempts = 5;

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public void setMaxAttempts(int maxAttempts) {
        this.maxAttempts = maxAttempts;
    }
}
