package com.fern.iamservice.config;

import java.time.Duration;

public class IamClientProperties {
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
    }
}
