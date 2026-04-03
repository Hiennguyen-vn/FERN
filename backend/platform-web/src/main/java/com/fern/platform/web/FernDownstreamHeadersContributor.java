package com.fern.platform.web;

import com.fern.platform.common.FernPrincipal;
import com.fern.platform.common.FernRequestHeaders;
import com.fern.platform.observability.CorrelationId;
import org.springframework.http.HttpHeaders;

@FunctionalInterface
public interface FernDownstreamHeadersContributor {
    void contribute(HttpHeaders headers);

    static FernDownstreamHeadersContributor none() {
        return headers -> {
        };
    }

    static FernDownstreamHeadersContributor bearerToken(
            String bearerToken,
            FernPrincipal actor,
            String correlationId
    ) {
        return headers -> {
            headers.set(HttpHeaders.AUTHORIZATION, "Bearer " + bearerToken);
            if (correlationId != null && !correlationId.isBlank()) {
                headers.set(CorrelationId.HEADER, correlationId);
            }
            if (actor != null && actor.userId() != null) {
                headers.set(FernRequestHeaders.ACTOR_USER_ID, actor.userId().toString());
            }
            if (actor != null && actor.username() != null) {
                headers.set(FernRequestHeaders.ACTOR_USERNAME, actor.username());
            }
        };
    }
}
