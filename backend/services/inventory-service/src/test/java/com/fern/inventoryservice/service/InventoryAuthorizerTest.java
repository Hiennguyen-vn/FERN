package com.fern.inventoryservice.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fern.platform.common.FernPrincipal;
import com.fern.platform.common.FernPrincipalType;
import com.fern.platform.common.ForbiddenException;
import com.fern.platform.common.ScopeRoots;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class InventoryAuthorizerTest {
    private final InventoryAuthorizer authorizer = new InventoryAuthorizer();

    @Test
    void shouldAllowDescendantOutletForRegionSubtreePrincipal() {
        FernPrincipal principal = principal(
                Set.of("inventory.balance.read"),
                new ScopeRoots(false, List.of(10L), List.of()),
                new ScopeRoots(false, List.of(10L, 11L), List.of(101L, 102L))
        );

        assertThatCode(() -> authorizer.requireOutletAccess(principal, 102L, "inventory.balance.read")).doesNotThrowAnyException();
    }

    @Test
    void shouldKeepOutletScopedPrincipalLimitedToAssignedOutlet() {
        FernPrincipal principal = principal(
                Set.of("inventory.balance.read"),
                new ScopeRoots(false, List.of(), List.of(101L)),
                new ScopeRoots(false, List.of(), List.of(101L))
        );

        assertThatThrownBy(() -> authorizer.requireOutletAccess(principal, 102L, "inventory.balance.read"))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("Outlet is outside the current scope");
    }

    private FernPrincipal principal(Set<String> permissions, ScopeRoots scopeRoots, ScopeRoots accessibleScope) {
        return new FernPrincipal(1L, "inventory-user", Set.of("staff"), permissions, scopeRoots, accessibleScope, 1L, 1L, "inventory-jti", FernPrincipalType.USER);
    }
}
