package com.fern.financeservice.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fern.platform.common.FernPrincipal;
import com.fern.platform.common.ScopeRoots;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class FinancePrincipalSupportTest {
    @Test
    void shouldReturnNullActorIdWhenPrincipalIsNull() {
        assertThat(FinancePrincipalSupport.actorId(null)).isNull();
    }

    @Test
    void shouldReturnUserIdWhenPrincipalIsPresent() {
        FernPrincipal principal = new FernPrincipal(
                42L,
                "finance-user",
                Set.of("finance"),
                Set.of("finance.payroll.read"),
                new ScopeRoots(false, List.of(1L), List.of(101L)),
                1L,
                1L,
                "finance-principal-jti"
        );

        assertThat(FinancePrincipalSupport.actorId(principal)).isEqualTo(42L);
    }
}
