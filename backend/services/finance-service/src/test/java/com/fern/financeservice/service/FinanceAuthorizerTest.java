package com.fern.financeservice.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fern.platform.common.FernPrincipal;
import com.fern.platform.common.FernPrincipalType;
import com.fern.platform.common.ForbiddenException;
import com.fern.platform.common.ScopeRoots;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class FinanceAuthorizerTest {
    private final FinanceAuthorizer authorizer = new FinanceAuthorizer();

    @Test
    void shouldAllowDescendantRegionForRegionSubtreePrincipal() {
        FernPrincipal principal = principal(
                Set.of("finance.payroll.read"),
                new ScopeRoots(false, List.of(10L), List.of()),
                new ScopeRoots(false, List.of(10L, 11L), List.of())
        );

        assertThatCode(() -> authorizer.requireRegionPermission(principal, 11L, "finance.payroll.read")).doesNotThrowAnyException();
    }

    @Test
    void shouldAllowSystemScopedPrincipal() {
        FernPrincipal principal = principal(
                Set.of("finance.payroll.read"),
                new ScopeRoots(true, List.of(), List.of()),
                new ScopeRoots(true, List.of(), List.of())
        );

        assertThatCode(() -> authorizer.requireSystemPermission(principal, "finance.payroll.read")).doesNotThrowAnyException();
    }

    @Test
    void shouldRejectOutOfScopeRegion() {
        FernPrincipal principal = principal(
                Set.of("finance.payroll.read"),
                new ScopeRoots(false, List.of(), List.of()),
                new ScopeRoots(false, List.of(), List.of())
        );

        assertThatThrownBy(() -> authorizer.requireRegionPermission(principal, 11L, "finance.payroll.read"))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("Region is outside the current scope");
    }

    private FernPrincipal principal(Set<String> permissions, ScopeRoots scopeRoots, ScopeRoots accessibleScope) {
        return new FernPrincipal(1L, "finance-user", Set.of("staff"), permissions, scopeRoots, accessibleScope, 1L, 1L, "finance-authorizer-jti", FernPrincipalType.USER);
    }
}
