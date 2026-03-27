package com.fern.posservice.service;

import com.fern.platform.common.FernPrincipal;
import com.fern.platform.common.FernPrincipalType;
import com.fern.platform.common.FernRequestHeaders;
import com.fern.platform.common.ScopeRoots;
import com.fern.platform.observability.CorrelationId;
import com.fern.platform.security.FernJwtClaims;
import com.fern.platform.security.FernJwtService;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Component
public class PosInternalClientSupport {
    private final FernJwtService jwtService;
    private final Clock clock;

    public PosInternalClientSupport(FernJwtService jwtService, Clock clock) {
        this.jwtService = jwtService;
        this.clock = clock;
    }

    public void applyInternalHeaders(HttpHeaders headers, FernPrincipal actor, Set<String> permissions) {
        headers.set(HttpHeaders.AUTHORIZATION, "Bearer " + issueServiceToken(permissions));
        if (actor != null && actor.userId() != null) {
            headers.set(FernRequestHeaders.ACTOR_USER_ID, actor.userId().toString());
        }
        if (actor != null && actor.username() != null) {
            headers.set(FernRequestHeaders.ACTOR_USERNAME, actor.username());
        }
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return;
        }
        HttpServletRequest request = attributes.getRequest();
        String correlationId = request.getHeader(CorrelationId.HEADER);
        if (correlationId != null) {
            headers.set(CorrelationId.HEADER, correlationId);
        }
    }

    private String issueServiceToken(Set<String> permissions) {
        Instant now = clock.instant();
        return jwtService.encode(
                new FernJwtClaims(
                        null,
                        PosServiceNames.POS_SERVICE,
                        Set.of(),
                        permissions,
                        new ScopeRoots(true, List.of(), List.of()),
                        0L,
                        0L,
                        UUID.randomUUID().toString(),
                        now,
                        now.plus(jwtService.serviceTokenTtl()),
                        FernPrincipalType.SERVICE
                ),
                jwtService.serviceTokenTtl()
        );
    }
}
