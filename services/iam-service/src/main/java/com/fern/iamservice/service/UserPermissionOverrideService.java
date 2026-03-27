package com.fern.iamservice.service;

import com.fern.iamservice.domain.UserPermissionOverrideEntity;
import com.fern.iamservice.dto.PermissionOverrideItemRequest;
import com.fern.iamservice.dto.PermissionOverrideItemResponse;
import com.fern.iamservice.dto.PutUserPermissionOverridesRequest;
import com.fern.iamservice.dto.UserPermissionOverridesResponse;
import com.fern.iamservice.repository.PermissionRepository;
import com.fern.iamservice.repository.UserPermissionOverrideRepository;
import com.fern.platform.common.BadRequestException;
import com.fern.platform.common.ConflictException;
import com.fern.platform.common.FernPrincipal;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserPermissionOverrideService {
    private final UserViewService userViewService;
    private final PermissionRepository permissionRepository;
    private final UserPermissionOverrideRepository overrideRepository;
    private final PolicyVersionService policyVersionService;
    private final IamAuditService iamAuditService;
    private final Clock clock;

    public UserPermissionOverrideService(
            UserViewService userViewService,
            PermissionRepository permissionRepository,
            UserPermissionOverrideRepository overrideRepository,
            PolicyVersionService policyVersionService,
            IamAuditService iamAuditService,
            Clock clock
    ) {
        this.userViewService = userViewService;
        this.permissionRepository = permissionRepository;
        this.overrideRepository = overrideRepository;
        this.policyVersionService = policyVersionService;
        this.iamAuditService = iamAuditService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public UserPermissionOverridesResponse get(Long userId) {
        userViewService.findUser(userId);
        return new UserPermissionOverridesResponse(userId, overrideRepository.findAllByUserId(userId).stream()
                .sorted(Comparator.comparing(UserPermissionOverrideEntity::getPermissionId))
                .map(this::toResponse)
                .toList());
    }

    @Transactional
    public UserPermissionOverridesResponse replace(FernPrincipal principal, Long userId, PutUserPermissionOverridesRequest request) {
        userViewService.findUser(userId);
        List<PermissionOverrideItemRequest> overrides = request.overrides() == null ? List.of() : request.overrides();
        validateNoDuplicatePermissionCodes(overrides);

        List<ResolvedOverride> resolvedOverrides = new ArrayList<>(overrides.size());
        for (PermissionOverrideItemRequest item : overrides) {
            var permission = permissionRepository.findByCode(item.permissionCode())
                    .orElseThrow(() -> new ConflictException("Permission code does not exist: " + item.permissionCode()));
            resolvedOverrides.add(new ResolvedOverride(item, permission.getId()));
        }

        overrideRepository.deleteByUserId(userId);
        for (ResolvedOverride resolvedOverride : resolvedOverrides) {
            PermissionOverrideItemRequest item = resolvedOverride.item();
            UserPermissionOverrideEntity entity = new UserPermissionOverrideEntity();
            entity.setUserId(userId);
            entity.setPermissionId(resolvedOverride.permissionId());
            entity.setOverrideMode(item.overrideMode());
            entity.setReason(item.reason());
            entity.setExpiresAt(item.expiresAt());
            entity.setCreatedByUserId(principal == null ? null : principal.userId());
            entity.setCreatedAt(clock.instant());
            entity.setUpdatedAt(clock.instant());
            overrideRepository.save(entity);
        }

        policyVersionService.bump();
        UserPermissionOverridesResponse response = get(userId);
        iamAuditService.permissionOverridesChanged(principal, userId, response);
        return response;
    }

    private void validateNoDuplicatePermissionCodes(List<PermissionOverrideItemRequest> overrides) {
        Set<String> seenPermissionCodes = new HashSet<>();
        for (PermissionOverrideItemRequest item : overrides) {
            if (!seenPermissionCodes.add(item.permissionCode())) {
                throw new BadRequestException("Duplicate permissionCode in request: " + item.permissionCode());
            }
        }
    }

    private PermissionOverrideItemResponse toResponse(UserPermissionOverrideEntity entity) {
        String permissionCode = permissionRepository.findById(entity.getPermissionId())
                .map(permission -> permission.getCode())
                .orElse("unknown");
        return new PermissionOverrideItemResponse(
                permissionCode,
                entity.getOverrideMode(),
                entity.getReason(),
                entity.getExpiresAt()
        );
    }

    private record ResolvedOverride(PermissionOverrideItemRequest item, Long permissionId) {
    }
}
