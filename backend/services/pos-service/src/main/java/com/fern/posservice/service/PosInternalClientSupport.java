package com.fern.posservice.service;

import com.fern.platform.common.FernPrincipal;
import com.fern.platform.common.FernRequestHeaders;
import com.fern.platform.observability.CorrelationId;
import com.fern.platform.security.FernServiceTokenSupport;
import java.util.Set;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

@Component
public class PosInternalClientSupport {
    private final FernServiceTokenSupport serviceTokenSupport;

    public PosInternalClientSupport(FernServiceTokenSupport serviceTokenSupport) {
        this.serviceTokenSupport = serviceTokenSupport;
    }

    public void applyInternalHeaders(HttpHeaders headers, FernPrincipal actor, String targetService, Set<String> permissions) {
        headers.set(
                HttpHeaders.AUTHORIZATION,
                "Bearer " + serviceTokenSupport.issueToken(PosServiceNames.POS_SERVICE, targetService, permissions)
        );
        if (actor != null && actor.userId() != null) {
            headers.set(FernRequestHeaders.ACTOR_USER_ID, actor.userId().toString());
        }
        if (actor != null && actor.username() != null) {
            headers.set(FernRequestHeaders.ACTOR_USERNAME, actor.username());
        }
        String correlationId = MDC.get(CorrelationId.MDC_KEY);
        if (correlationId != null) {
            headers.set(CorrelationId.HEADER, correlationId);
        }
    }
}
