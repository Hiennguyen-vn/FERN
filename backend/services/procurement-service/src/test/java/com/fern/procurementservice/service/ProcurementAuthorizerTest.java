package com.fern.procurementservice.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fern.platform.common.FernPrincipal;
import com.fern.platform.common.FernPrincipalType;
import com.fern.platform.common.ForbiddenException;
import com.fern.platform.common.ScopeRoots;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ProcurementAuthorizerTest {
    private final ProcurementAuthorizer authorizer = new ProcurementAuthorizer();

    @Test
    void shouldAllowDescendantRouteForRegionSubtreePrincipal() {
        FernPrincipal principal = principal(
                Set.of("procurement.po.read"),
                new ScopeRoots(false, List.of(10L), List.of()),
                new ScopeRoots(false, List.of(10L, 11L), List.of(101L, 102L))
        );

        assertThatCode(() -> authorizer.requireRouteRead(principal, 11L, 102L, "procurement.po.read")).doesNotThrowAnyException();
    }

    @Test
    void shouldKeepOutletScopedPrincipalLimitedToAssignedOutlet() {
        FernPrincipal principal = principal(
                Set.of("procurement.po.read"),
                new ScopeRoots(false, List.of(), List.of(101L)),
                new ScopeRoots(false, List.of(), List.of(101L))
        );

        assertThatThrownBy(() -> authorizer.requireRouteRead(principal, 11L, 102L, "procurement.po.read"))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("Resource is outside the current scope");
    }

    private FernPrincipal principal(Set<String> permissions, ScopeRoots scopeRoots, ScopeRoots accessibleScope) {
        return new FernPrincipal(1L, "procurement-user", Set.of("staff"), permissions, scopeRoots, accessibleScope, 1L, 1L, "procurement-jti", FernPrincipalType.USER);
    }
}
