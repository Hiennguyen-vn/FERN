package com.fern.platform.security;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fern.platform.common.ScopeRoots;
import com.fern.platform.common.UnauthorizedException;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

class StaleTokenAcceptanceSecurityGapTest {
    @Test
    @Tag("security-gap")
    void shouldFailClosedWhenBlacklistSnapshotIsStale() {
        MutableTokenState state = new MutableTokenState(false, 1L, 1L);
        FernJwtClaims claims = userClaims(1L, 1L);
        FernTokenAcceptanceKnowledge knowledge = new InMemoryFernTokenAcceptanceKnowledge();
        FernTokenAcceptanceValidator liveValidator = new LiveTokenAcceptanceValidator(state, knowledge);
        FernTokenAcceptanceValidator staleValidator = new SnapshotTokenAcceptanceValidator(state, knowledge);

        state.blacklisted = true;

        assertThatThrownBy(() -> liveValidator.validate(claims)).isInstanceOf(UnauthorizedException.class);
        assertThatThrownBy(() -> staleValidator.validate(claims)).isInstanceOf(UnauthorizedException.class);
    }

    @Test
    @Tag("security-gap")
    void shouldFailClosedWhenPolicyVersionSnapshotIsStale() {
        MutableTokenState state = new MutableTokenState(false, 1L, 1L);
        FernJwtClaims claims = userClaims(1L, 1L);
        FernTokenAcceptanceKnowledge knowledge = new InMemoryFernTokenAcceptanceKnowledge();
        FernTokenAcceptanceValidator liveValidator = new LiveTokenAcceptanceValidator(state, knowledge);
        FernTokenAcceptanceValidator staleValidator = new SnapshotTokenAcceptanceValidator(state, knowledge);

        state.policyVersion = 2L;

        assertThatThrownBy(() -> liveValidator.validate(claims)).isInstanceOf(UnauthorizedException.class);
        assertThatThrownBy(() -> staleValidator.validate(claims)).isInstanceOf(UnauthorizedException.class);
    }

    @Test
    @Tag("security-gap")
    void shouldFailClosedWhenScopeVersionSnapshotIsStale() {
        MutableTokenState state = new MutableTokenState(false, 1L, 1L);
        FernJwtClaims claims = userClaims(1L, 1L);
        FernTokenAcceptanceKnowledge knowledge = new InMemoryFernTokenAcceptanceKnowledge();
        FernTokenAcceptanceValidator liveValidator = new LiveTokenAcceptanceValidator(state, knowledge);
        FernTokenAcceptanceValidator staleValidator = new SnapshotTokenAcceptanceValidator(state, knowledge);

        state.scopeVersion = 2L;

        assertThatThrownBy(() -> liveValidator.validate(claims)).isInstanceOf(UnauthorizedException.class);
        assertThatThrownBy(() -> staleValidator.validate(claims)).isInstanceOf(UnauthorizedException.class);
    }

    private FernJwtClaims userClaims(long policyVersion, long scopeVersion) {
        Instant now = Instant.parse("2026-03-28T00:00:00Z");
        return new FernJwtClaims(
                100L,
                "security-gap-user",
                Set.of("finance"),
                Set.of("finance.payroll.read"),
                new ScopeRoots(false, List.of(1L), List.of()),
                policyVersion,
                scopeVersion,
                UUID.randomUUID().toString(),
                now,
                now.plusSeconds(900),
                com.fern.platform.common.FernPrincipalType.USER,
                FernJwtProperties.DEFAULT_USER_TOKEN_ISSUER,
                Set.of("finance-service")
        );
    }

    private static void validateAgainstState(
            FernJwtClaims claims,
            MutableTokenState state,
            FernTokenAcceptanceKnowledge knowledge,
            boolean authoritative
    ) {
        FernJwtClaimValidationRules.validateIssuerAndAudience(
                claims,
                "finance-service",
                FernJwtProperties.DEFAULT_USER_TOKEN_ISSUER
        );
        if (!FernTokenAcceptanceRules.isAccepted(
                claims,
                state.blacklisted,
                state.policyVersion,
                state.scopeVersion,
                knowledge,
                authoritative
        )) {
            throw new UnauthorizedException("Token is revoked or stale");
        }
    }

    private static final class LiveTokenAcceptanceValidator implements FernTokenAcceptanceValidator {
        private final MutableTokenState state;
        private final FernTokenAcceptanceKnowledge knowledge;

        private LiveTokenAcceptanceValidator(MutableTokenState state, FernTokenAcceptanceKnowledge knowledge) {
            this.state = state;
            this.knowledge = knowledge;
        }

        @Override
        public void validate(FernJwtClaims claims) {
            validateAgainstState(claims, state, knowledge, true);
        }
    }

    private static final class SnapshotTokenAcceptanceValidator implements FernTokenAcceptanceValidator {
        private final MutableTokenState snapshot;
        private final FernTokenAcceptanceKnowledge knowledge;

        private SnapshotTokenAcceptanceValidator(MutableTokenState state, FernTokenAcceptanceKnowledge knowledge) {
            this.snapshot = new MutableTokenState(state.blacklisted, state.policyVersion, state.scopeVersion);
            this.knowledge = knowledge;
        }

        @Override
        public void validate(FernJwtClaims claims) {
            validateAgainstState(claims, snapshot, knowledge, false);
        }
    }

    private static final class MutableTokenState {
        private boolean blacklisted;
        private long policyVersion;
        private long scopeVersion;

        private MutableTokenState(boolean blacklisted, long policyVersion, long scopeVersion) {
            this.blacklisted = blacklisted;
            this.policyVersion = policyVersion;
            this.scopeVersion = scopeVersion;
        }
    }
}
