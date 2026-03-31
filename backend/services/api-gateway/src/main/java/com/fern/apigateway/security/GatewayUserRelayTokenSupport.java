package com.fern.apigateway.security;

import com.fern.platform.common.FernPrincipalType;
import com.fern.platform.security.FernJwtClaims;
import com.fern.platform.security.FernJwtProperties;
import com.fern.platform.security.FernJwtService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class GatewayUserRelayTokenSupport {
    private final FernJwtService jwtService;
    private final Clock clock;
    private final Duration relayTokenTtl;
    private final String relayIssuer;

    public GatewayUserRelayTokenSupport(FernJwtService jwtService, Clock clock, FernJwtProperties jwtProperties) {
        this.jwtService = jwtService;
        this.clock = clock;
        this.relayTokenTtl = Duration.ofSeconds(jwtProperties.getGatewayRelayUserTokenTtlSeconds());
        this.relayIssuer = jwtProperties.getGatewayRelayUserIssuer();
    }

    public String issueRelayToken(FernJwtClaims sourceClaims, String targetService) {
        Instant now = clock.instant();
        Duration remainingLifetime = Duration.between(now, sourceClaims.expiresAt());
        Duration ttl = clampLifetime(remainingLifetime);
        return jwtService.encode(
                new FernJwtClaims(
                        sourceClaims.userId(),
                        sourceClaims.username(),
                        sourceClaims.roles(),
                        sourceClaims.permissions(),
                        sourceClaims.scopeRoots(),
                        sourceClaims.accessibleScope(),
                        sourceClaims.policyVersion(),
                        sourceClaims.scopeVersion(),
                        sourceClaims.jti(),
                        sourceClaims.authTime(),
                        now.plus(ttl),
                        FernPrincipalType.USER,
                        relayIssuer,
                        Set.of(targetService)
                ),
                ttl
        );
    }

    private Duration clampLifetime(Duration remainingLifetime) {
        if (remainingLifetime.isNegative() || remainingLifetime.isZero()) {
            return Duration.ofSeconds(1);
        }
        return remainingLifetime.compareTo(relayTokenTtl) < 0 ? remainingLifetime : relayTokenTtl;
    }
}
