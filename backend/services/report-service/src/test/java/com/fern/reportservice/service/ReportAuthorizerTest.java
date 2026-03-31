package com.fern.reportservice.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fern.platform.common.FernPrincipal;
import com.fern.platform.common.FernPrincipalType;
import com.fern.platform.common.ForbiddenException;
import com.fern.platform.common.PermissionCodes;
import com.fern.platform.common.ScopeRoots;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ReportAuthorizerTest {
    private final ReportAuthorizer authorizer = new ReportAuthorizer();

    @Test
    void shouldAllowDescendantRegionForRegionSubtreePrincipal() {
        FernPrincipal principal = principal(
                Set.of(PermissionCodes.REPORT_READ),
                new ScopeRoots(false, List.of(10L), List.of()),
                new ScopeRoots(false, List.of(10L, 11L), List.of())
        );

        assertThatCode(() -> authorizer.requireRegionReportRead(principal, 11L)).doesNotThrowAnyException();
    }

    @Test
    void shouldRejectOutOfScopeRegion() {
        FernPrincipal principal = principal(
                Set.of(PermissionCodes.REPORT_READ),
                new ScopeRoots(false, List.of(10L), List.of()),
                new ScopeRoots(false, List.of(10L), List.of())
        );

        assertThatThrownBy(() -> authorizer.requireRegionReportRead(principal, 11L))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("Region is outside the current scope");
    }

    private FernPrincipal principal(Set<String> permissions, ScopeRoots scopeRoots, ScopeRoots accessibleScope) {
        return new FernPrincipal(1L, "report-user", Set.of("staff"), permissions, scopeRoots, accessibleScope, 1L, 1L, "report-authorizer-jti", FernPrincipalType.USER);
    }
}
