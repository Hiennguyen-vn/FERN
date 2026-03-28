package com.fern.platform.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "fern.security.jwt")
public class FernJwtProperties {
    public static final String INSECURE_DEFAULT_SECRET = "XV4T89da-00NoHY48hZTYhGdaCNpqooKVy4MDKTRO5v4Im6TwlAITKb6_O4K--Iv";

    private String secret;
    private long accessTokenTtlSeconds = 900;
    private long refreshTokenTtlSeconds = 604800;
    private long serviceTokenTtlSeconds = 300;
    private boolean allowInsecureDefaultSecret;

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public long getAccessTokenTtlSeconds() {
        return accessTokenTtlSeconds;
    }

    public void setAccessTokenTtlSeconds(long accessTokenTtlSeconds) {
        this.accessTokenTtlSeconds = accessTokenTtlSeconds;
    }

    public long getRefreshTokenTtlSeconds() {
        return refreshTokenTtlSeconds;
    }

    public void setRefreshTokenTtlSeconds(long refreshTokenTtlSeconds) {
        this.refreshTokenTtlSeconds = refreshTokenTtlSeconds;
    }

    public long getServiceTokenTtlSeconds() {
        return serviceTokenTtlSeconds;
    }

    public void setServiceTokenTtlSeconds(long serviceTokenTtlSeconds) {
        this.serviceTokenTtlSeconds = serviceTokenTtlSeconds;
    }

    public boolean isAllowInsecureDefaultSecret() {
        return allowInsecureDefaultSecret;
    }

    public void setAllowInsecureDefaultSecret(boolean allowInsecureDefaultSecret) {
        this.allowInsecureDefaultSecret = allowInsecureDefaultSecret;
    }
}
