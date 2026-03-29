package com.fern.procurementservice.config;

import java.time.Duration;

public class ProcurementClientProperties {
    private ClientProperties org = new ClientProperties("http://localhost:8082");

    public ClientProperties getOrg() {
        return org;
    }

    public void setOrg(ClientProperties org) {
        this.org = org;
    }

    public static class ClientProperties {
        private String baseUrl;
        private Duration connectTimeout = Duration.ofSeconds(2);
        private Duration readTimeout = Duration.ofSeconds(5);
        private float failureRateThreshold = 50;
        private int slidingWindowSize = 10;
        private int minimumNumberOfCalls = 5;
        private long waitDurationOpenSeconds = 30;

        public ClientProperties() {
        }

        public ClientProperties(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public Duration getConnectTimeout() {
            return connectTimeout;
        }

        public void setConnectTimeout(Duration connectTimeout) {
            this.connectTimeout = connectTimeout;
        }

        public Duration getReadTimeout() {
            return readTimeout;
        }

        public void setReadTimeout(Duration readTimeout) {
            this.readTimeout = readTimeout;
        }

        public float getFailureRateThreshold() {
            return failureRateThreshold;
        }

        public void setFailureRateThreshold(float failureRateThreshold) {
            this.failureRateThreshold = failureRateThreshold;
        }

        public int getSlidingWindowSize() {
            return slidingWindowSize;
        }

        public void setSlidingWindowSize(int slidingWindowSize) {
            this.slidingWindowSize = slidingWindowSize;
        }

        public int getMinimumNumberOfCalls() {
            return minimumNumberOfCalls;
        }

        public void setMinimumNumberOfCalls(int minimumNumberOfCalls) {
            this.minimumNumberOfCalls = minimumNumberOfCalls;
        }

        public long getWaitDurationOpenSeconds() {
            return waitDurationOpenSeconds;
        }

        public void setWaitDurationOpenSeconds(long waitDurationOpenSeconds) {
            this.waitDurationOpenSeconds = waitDurationOpenSeconds;
        }
    }
}
