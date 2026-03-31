package com.fern.iamservice.service;

import com.fern.iamservice.domain.UserAccountEntity;
import com.fern.iamservice.dto.UserPermissionOverridesResponse;
import com.fern.platform.audit.AuditEvent;
import com.fern.platform.audit.AuditEventPublisher;
import com.fern.platform.audit.SecurityEvent;
import com.fern.platform.audit.SensitiveDataMasker;
import com.fern.platform.common.FernPrincipal;
import com.fern.platform.common.ScopeAccess;
import com.fern.platform.common.ScopeRoots;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class IamAuditService {
    private final AuditEventPublisher auditEventPublisher;
    private final Clock clock;

    public IamAuditService(AuditEventPublisher auditEventPublisher, Clock clock) {
        this.auditEventPublisher = auditEventPublisher;
        this.clock = clock;
    }

    public void publishSecurityEvent(
            String eventType,
            Long userId,
            String outcome,
            String failureReason,
            Map<String, Object> payload,
            IamRequestMetadata requestMetadata
    ) {
        IamRequestMetadata effectiveRequestMetadata = requestMetadata == null ? IamRequestMetadata.empty() : requestMetadata;
        auditEventPublisher.publishSecurityEvent(new SecurityEvent(
                UUID.randomUUID().toString(),
                eventType,
                clock.instant(),
                "iam-service",
                effectiveRequestMetadata.correlationId(),
                userId,
                outcome,
                failureReason,
                effectiveRequestMetadata.ipAddress(),
                effectiveRequestMetadata.userAgent(),
                UUID.randomUUID().toString(),
                mask(payload)
        ));
    }

    public void publishAuditEvent(
            String eventType,
            FernPrincipal principal,
            Long userId,
            String action,
            String resourceType,
            String resourceId,
            String outcome,
            Object oldValue,
            Object newValue,
            Map<String, Object> payload,
            IamRequestMetadata requestMetadata
    ) {
        IamRequestMetadata effectiveRequestMetadata = requestMetadata == null ? IamRequestMetadata.empty() : requestMetadata;
        auditEventPublisher.publishAuditEvent(new AuditEvent(
                UUID.randomUUID().toString(),
                eventType,
                clock.instant(),
                "iam-service",
                effectiveRequestMetadata.correlationId(),
                principal != null ? principal.userId() : userId,
                firstRegionId(principal),
                firstOutletId(principal),
                action,
                resourceType,
                resourceId,
                outcome,
                SensitiveDataMasker.mask(oldValue),
                SensitiveDataMasker.mask(newValue),
                UUID.randomUUID().toString(),
                mask(payload)
        ));
    }

    public void userCreated(UserAccountEntity user, Object newValue, IamRequestMetadata requestMetadata) {
        publishAuditEvent(
                "iam.user.created",
                null,
                user.getId(),
                "CREATE_USER",
                "user_account",
                String.valueOf(user.getId()),
                "SUCCESS",
                null,
                newValue,
                Map.of("username", user.getUsername()),
                requestMetadata
        );
    }

    public void userStatusChanged(
            FernPrincipal principal,
            UserAccountEntity user,
            Object oldValue,
            Object newValue,
            IamRequestMetadata requestMetadata
    ) {
        publishAuditEvent(
                "iam.user.status_changed",
                principal,
                user.getId(),
                "UPDATE_USER_STATUS",
                "user_account",
                String.valueOf(user.getId()),
                "SUCCESS",
                oldValue,
                newValue,
                Map.of("username", user.getUsername()),
                requestMetadata
        );
    }

    public void userUpdated(
            FernPrincipal principal,
            UserAccountEntity user,
            Object oldValue,
            Object newValue,
            IamRequestMetadata requestMetadata
    ) {
        publishAuditEvent(
                "iam.user.changed",
                principal,
                user.getId(),
                "UPDATE_USER",
                "user_account",
                String.valueOf(user.getId()),
                "SUCCESS",
                oldValue,
                newValue,
                Map.of("username", user.getUsername()),
                requestMetadata
        );
    }

    public void userAccessChanged(
            FernPrincipal principal,
            Long userId,
            String action,
            Object newValue,
            IamRequestMetadata requestMetadata
    ) {
        publishAuditEvent(
                "iam.user.access_changed",
                principal,
                userId,
                action,
                "user_account",
                String.valueOf(userId),
                "SUCCESS",
                null,
                newValue,
                Map.of(),
                requestMetadata
        );
    }

    public void permissionOverridesChanged(
            FernPrincipal principal,
            Long userId,
            UserPermissionOverridesResponse response,
            IamRequestMetadata requestMetadata
    ) {
        publishAuditEvent(
                "iam.user.permission_override.changed",
                principal,
                userId,
                "PUT_PERMISSION_OVERRIDE",
                "user_permission_override",
                String.valueOf(userId),
                "SUCCESS",
                null,
                response,
                Map.of("overrideCount", response.overrides().size()),
                requestMetadata
        );
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> mask(Map<String, Object> payload) {
        Object masked = SensitiveDataMasker.mask(payload == null ? Map.of() : payload);
        return masked instanceof Map<?, ?> map ? (Map<String, Object>) map : new LinkedHashMap<>();
    }

    private Long firstRegionId(FernPrincipal principal) {
        ScopeRoots accessibleScope = principal == null ? ScopeRoots.empty() : ScopeAccess.accessibleScope(principal);
        return accessibleScope.regions().isEmpty() ? null : accessibleScope.regions().getFirst();
    }

    private Long firstOutletId(FernPrincipal principal) {
        ScopeRoots accessibleScope = principal == null ? ScopeRoots.empty() : ScopeAccess.accessibleScope(principal);
        return accessibleScope.outlets().isEmpty() ? null : accessibleScope.outlets().getFirst();
    }
}
