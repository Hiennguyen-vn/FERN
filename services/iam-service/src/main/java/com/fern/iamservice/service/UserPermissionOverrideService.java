package com.fern.iamservice.service;

import com.fern.iamservice.domain.UserPermissionOverrideEntity;
import com.fern.iamservice.dto.PermissionOverrideItemRequest;
import com.fern.iamservice.dto.PermissionOverrideItemResponse;
import com.fern.iamservice.dto.PutUserPermissionOverridesRequest;
import com.fern.iamservice.dto.UserPermissionOverridesResponse;
import com.fern.iamservice.repository.PermissionRepository;
import com.fern.iamservice.repository.UserPermissionOverrideRepository;
import com.fern.platform.common.ConflictException;
import com.fern.platform.common.FernPrincipal;
import java.time.Clock;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserPermissionOverrideService {
    private final UserViewService userViewService;
    private final PermissionRepository permissionRepository;
    private final UserPermissionOverrideRepository overrideRepository;
    private final IamAuditService iamAuditService;
    private final Clock clock;

    public UserPermissionOverrideService(
            UserViewService userViewService,
            PermissionRepository permissionRepository,
            UserPermissionOverrideRepository overrideRepository,
            IamAuditService iamAuditService,
            Clock clock
    ) {
        this.userViewService = userViewService;
        this.permissionRepository = permissionRepository;
        this.overrideRepository = overrideRepository;
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
        overrideRepository.deleteByUserId(userId);

        List<PermissionOverrideItemRequest> overrides = request.overrides() == null ? List.of() : request.overrides();
        for (PermissionOverrideItemRequest item : overrides) {
            var permission = permissionRepository.findByCode(item.permissionCode())
                    .orElseThrow(() -> new ConflictException("Permission code does not exist: " + item.permissionCode()));
            UserPermissionOverrideEntity entity = new UserPermissionOverrideEntity();
            entity.setUserId(userId);
            entity.setPermissionId(permission.getId());
            entity.setOverrideMode(item.overrideMode());
            entity.setReason(item.reason());
            entity.setExpiresAt(item.expiresAt());
            entity.setCreatedByUserId(principal == null ? null : principal.userId());
            entity.setCreatedAt(clock.instant());
            entity.setUpdatedAt(clock.instant());
            overrideRepository.save(entity);
        }

        UserPermissionOverridesResponse response = get(userId);
        iamAuditService.permissionOverridesChanged(principal, userId, response);
        return response;
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
}
