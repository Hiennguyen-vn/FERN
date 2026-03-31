package com.fern.posservice.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fern.platform.common.FernPrincipal;
import com.fern.platform.common.FernPrincipalType;
import com.fern.platform.common.ForbiddenException;
import com.fern.platform.common.ScopeRoots;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PosAuthorizerTest {
    private final PosAuthorizer authorizer = new PosAuthorizer();

    @Test
    void shouldAllowSystemScopedManager() {
        FernPrincipal principal = principal(
                Set.of("pos.session.open"),
                new ScopeRoots(true, List.of(), List.of()),
                new ScopeRoots(true, List.of(), List.of())
        );

        assertThatCode(() -> authorizer.requireRoutePermission(principal, 11L, 102L, "pos.session.open")).doesNotThrowAnyException();
    }

    @Test
    void shouldAllowRegionSubtreeManagerWithoutExplicitOutletMembership() {
        FernPrincipal principal = principal(
                Set.of("pos.session.open"),
                new ScopeRoots(false, List.of(10L), List.of()),
                new ScopeRoots(false, List.of(10L, 11L), List.of(101L, 102L))
        );

        assertThatCode(() -> authorizer.requireRoutePermission(principal, 11L, 102L, "pos.session.open")).doesNotThrowAnyException();
    }

    @Test
    void shouldAllowRegionSubtreeManagerToListExpandedOutlet() {
        FernPrincipal principal = principal(
                Set.of("pos.session.read"),
                new ScopeRoots(false, List.of(10L), List.of()),
                new ScopeRoots(false, List.of(10L, 11L), List.of(101L, 102L))
        );

        assertThatCode(() -> authorizer.requireOutletPermission(principal, 102L, "pos.session.read")).doesNotThrowAnyException();
    }

    @Test
    void shouldAllowOutletOnlyPrincipalWithoutRegionRoot() {
        FernPrincipal principal = principal(
                Set.of("pos.session.open"),
                new ScopeRoots(false, List.of(), List.of(101L)),
                new ScopeRoots(false, List.of(), List.of(101L))
        );

        assertThatCode(() -> authorizer.requireRoutePermission(principal, 11L, 101L, "pos.session.open")).doesNotThrowAnyException();
    }

    @Test
    void shouldAllowMixedScopeUserWhenOutletMatchesDespiteDifferentRegionRoot() {
        FernPrincipal principal = principal(
                Set.of("pos.session.open"),
                new ScopeRoots(false, List.of(999L), List.of(101L)),
                new ScopeRoots(false, List.of(999L), List.of(101L))
        );

        assertThatCode(() -> authorizer.requireRoutePermission(principal, 11L, 101L, "pos.session.open")).doesNotThrowAnyException();
    }

    @Test
    void shouldRejectPrincipalWithoutRegionOrOutletAccess() {
        FernPrincipal principal = principal(
                Set.of("pos.session.open"),
                ScopeRoots.empty(),
                ScopeRoots.empty()
        );

        assertThatThrownBy(() -> authorizer.requireRoutePermission(principal, 11L, 102L, "pos.session.open"))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("Outlet is outside the current scope");
    }

    private FernPrincipal principal(Set<String> permissions, ScopeRoots scopeRoots, ScopeRoots accessibleScope) {
        return new FernPrincipal(1L, "pos-user", Set.of("staff"), permissions, scopeRoots, accessibleScope, 1L, 1L, "pos-authorizer-jti", FernPrincipalType.USER);
    }
}
