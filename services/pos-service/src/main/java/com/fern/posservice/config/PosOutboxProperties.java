package com.fern.posservice.config;

import java.time.Duration;

public class PosOutboxProperties {
    private int maxAttempts = 5;
    private Duration reclaimAfter = Duration.ofMinutes(1);

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public void setMaxAttempts(int maxAttempts) {
        this.maxAttempts = maxAttempts;
    }

    public Duration getReclaimAfter() {
        return reclaimAfter;
    }

    public void setReclaimAfter(Duration reclaimAfter) {
        this.reclaimAfter = reclaimAfter;
    }
}
