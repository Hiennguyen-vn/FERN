package com.fern.platform.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ScopeAccessTest {
    @Test
    void shouldAllowDescendantOutletFromAccessibleScope() {
        FernPrincipal principal = principal(
                new ScopeRoots(false, List.of(10L), List.of()),
                new ScopeRoots(false, List.of(10L, 11L), List.of(101L, 102L))
        );

        assertThat(ScopeAccess.allowsOutlet(principal, 102L)).isTrue();
        assertThat(ScopeAccess.allowsRegion(principal, 11L)).isTrue();
        assertThat(ScopeAccess.allowsRoute(principal, 11L, 102L)).isTrue();
    }

    @Test
    void shouldKeepOutletOnlyUsersOutletLimited() {
        FernPrincipal principal = principal(
                new ScopeRoots(false, List.of(), List.of(101L)),
                new ScopeRoots(false, List.of(), List.of(101L))
        );

        assertThat(ScopeAccess.allowsOutlet(principal, 101L)).isTrue();
        assertThat(ScopeAccess.allowsOutlet(principal, 102L)).isFalse();
        assertThat(ScopeAccess.allowsRegion(principal, 11L)).isFalse();
    }

    private FernPrincipal principal(ScopeRoots scopeRoots, ScopeRoots accessibleScope) {
        return new FernPrincipal(
                1L,
                "regional-manager",
                Set.of("regional_manager"),
                Set.of("inventory.balance.read"),
                scopeRoots,
                accessibleScope,
                1L,
                1L,
                "scope-access-jti",
                FernPrincipalType.USER
        );
    }
}
