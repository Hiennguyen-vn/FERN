package com.fern.notificationservice.config;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "fern.notification")
public class NotificationProperties {
    private final OpsWebhook opsWebhook = new OpsWebhook();
    private final Retry retry = new Retry();
    private List<String> dlqTopics = new ArrayList<>();

    public OpsWebhook getOpsWebhook() {
        return opsWebhook;
    }

    public Retry getRetry() {
        return retry;
    }

    public List<String> getDlqTopics() {
        return dlqTopics;
    }

    public void setDlqTopics(List<String> dlqTopics) {
        this.dlqTopics = dlqTopics == null ? new ArrayList<>() : new ArrayList<>(dlqTopics);
    }

    public static class OpsWebhook {
        private String url;
        private String secret;

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public String getSecret() {
            return secret;
        }

        public void setSecret(String secret) {
            this.secret = secret;
        }
    }

    public static class Retry {
        private int maxAttempts = 5;
        private long delayMs = 10000;

        public int getMaxAttempts() {
            return maxAttempts;
        }

        public void setMaxAttempts(int maxAttempts) {
            this.maxAttempts = maxAttempts;
        }

        public long getDelayMs() {
            return delayMs;
        }

        public void setDelayMs(long delayMs) {
            this.delayMs = delayMs;
        }
    }
}
