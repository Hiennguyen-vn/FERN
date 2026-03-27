package com.fern.posservice.config;

import java.time.Duration;

public class PosClientProperties {
    private ClientProperties catalog = new ClientProperties("http://localhost:8085");
    private ClientProperties inventory = new ClientProperties("http://localhost:8087");

    public ClientProperties getCatalog() {
        return catalog;
    }

    public void setCatalog(ClientProperties catalog) {
        this.catalog = catalog;
    }

    public ClientProperties getInventory() {
        return inventory;
    }

    public void setInventory(ClientProperties inventory) {
        this.inventory = inventory;
    }

    public static class ClientProperties {
        private String baseUrl;
        private Duration connectTimeout = Duration.ofSeconds(2);
        private Duration readTimeout = Duration.ofSeconds(5);
        private CircuitBreakerProperties circuitBreaker = new CircuitBreakerProperties();

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

        public CircuitBreakerProperties getCircuitBreaker() {
            return circuitBreaker;
        }

        public void setCircuitBreaker(CircuitBreakerProperties circuitBreaker) {
            this.circuitBreaker = circuitBreaker;
        }
    }

    public static class CircuitBreakerProperties {
        private float failureRateThreshold = 50.0f;
        private int minimumNumberOfCalls = 4;
        private int slidingWindowSize = 8;
        private Duration waitDurationInOpenState = Duration.ofSeconds(15);

        public float getFailureRateThreshold() {
            return failureRateThreshold;
        }

        public void setFailureRateThreshold(float failureRateThreshold) {
            this.failureRateThreshold = failureRateThreshold;
        }

        public int getMinimumNumberOfCalls() {
            return minimumNumberOfCalls;
        }

        public void setMinimumNumberOfCalls(int minimumNumberOfCalls) {
            this.minimumNumberOfCalls = minimumNumberOfCalls;
        }

        public int getSlidingWindowSize() {
            return slidingWindowSize;
        }

        public void setSlidingWindowSize(int slidingWindowSize) {
            this.slidingWindowSize = slidingWindowSize;
        }

        public Duration getWaitDurationInOpenState() {
            return waitDurationInOpenState;
        }

        public void setWaitDurationInOpenState(Duration waitDurationInOpenState) {
            this.waitDurationInOpenState = waitDurationInOpenState;
        }
    }
}
