package com.fern.hrservice.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fern.platform.common.FernPrincipal;
import com.fern.platform.common.FernPrincipalType;
import com.fern.platform.common.ForbiddenException;
import com.fern.platform.common.ScopeRoots;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class HrAuthorizerTest {
    private final HrAuthorizer authorizer = new HrAuthorizer();

    @Test
    void shouldAllowDescendantRegionForRegionSubtreePrincipal() {
        FernPrincipal principal = principal(
                Set.of("hr.shift.read"),
                new ScopeRoots(false, List.of(10L), List.of()),
                new ScopeRoots(false, List.of(10L, 11L), List.of(101L, 102L))
        );

        assertThatCode(() -> authorizer.requireRegionPermission(principal, 11L, "hr.shift.read")).doesNotThrowAnyException();
    }

    @Test
    void shouldKeepOutletScopedPrincipalOutletLimited() {
        FernPrincipal principal = principal(
                Set.of("hr.shift.read"),
                new ScopeRoots(false, List.of(), List.of(101L)),
                new ScopeRoots(false, List.of(), List.of(101L))
        );

        assertThatThrownBy(() -> authorizer.requireOutletPermission(principal, 102L, "hr.shift.read"))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("Outlet is outside the current scope");
    }

    private FernPrincipal principal(Set<String> permissions, ScopeRoots scopeRoots, ScopeRoots accessibleScope) {
        return new FernPrincipal(1L, "hr-user", Set.of("staff"), permissions, scopeRoots, accessibleScope, 1L, 1L, "hr-authorizer-jti", FernPrincipalType.USER);
    }
}
