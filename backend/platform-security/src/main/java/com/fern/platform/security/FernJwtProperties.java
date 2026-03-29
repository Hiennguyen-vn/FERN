package com.fern.platform.security;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "fern.security.jwt")
public class FernJwtProperties {
    public static final String INSECURE_DEFAULT_SECRET = "XV4T89da-00NoHY48hZTYhGdaCNpqooKVy4MDKTRO5v4Im6TwlAITKb6_O4K--Iv";
    public static final String DEFAULT_USER_TOKEN_ISSUER = "iam-service";
    public static final List<String> DEFAULT_USER_TOKEN_AUDIENCES = List.of(
            "api-gateway",
            "iam-service",
            "org-service",
            "catalog-service",
            "pos-service",
            "inventory-service",
            "procurement-service",
            "hr-service",
            "finance-service",
            "report-service",
            "audit-service",
            "notification-service"
    );

    private String secret;
    private long accessTokenTtlSeconds = 900;
    private long refreshTokenTtlSeconds = 604800;
    private long serviceTokenTtlSeconds = 300;
    private boolean allowInsecureDefaultSecret;
    private String userTokenIssuer = DEFAULT_USER_TOKEN_ISSUER;
    private List<String> userTokenAudiences = DEFAULT_USER_TOKEN_AUDIENCES;

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

    public String getUserTokenIssuer() {
        return userTokenIssuer;
    }

    public void setUserTokenIssuer(String userTokenIssuer) {
        this.userTokenIssuer = userTokenIssuer;
    }

    public List<String> getUserTokenAudiences() {
        return userTokenAudiences;
    }

    public void setUserTokenAudiences(List<String> userTokenAudiences) {
        this.userTokenAudiences = userTokenAudiences;
    }
}
