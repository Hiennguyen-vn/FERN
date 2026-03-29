package com.fern.financeservice.service;

import com.fern.platform.common.FernPrincipal;

final class FinancePrincipalSupport {
    private FinancePrincipalSupport() {
    }

    static Long actorId(FernPrincipal principal) {
        return principal == null ? null : principal.userId();
    }
}
