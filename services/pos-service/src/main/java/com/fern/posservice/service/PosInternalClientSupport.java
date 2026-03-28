package com.fern.posservice.service;

import com.fern.platform.common.FernPrincipal;
import com.fern.platform.common.FernRequestHeaders;
import com.fern.platform.observability.CorrelationId;
import com.fern.platform.security.FernServiceTokenSupport;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Set;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Component
public class PosInternalClientSupport {
    private final FernServiceTokenSupport serviceTokenSupport;

    public PosInternalClientSupport(FernServiceTokenSupport serviceTokenSupport) {
        this.serviceTokenSupport = serviceTokenSupport;
    }

    public void applyInternalHeaders(HttpHeaders headers, FernPrincipal actor, Set<String> permissions) {
        headers.set(HttpHeaders.AUTHORIZATION, "Bearer " + serviceTokenSupport.issueToken(PosServiceNames.POS_SERVICE, permissions));
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
}
