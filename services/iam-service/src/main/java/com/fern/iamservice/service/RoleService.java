package com.fern.iamservice.service;

import com.fern.iamservice.domain.RoleEntity;
import com.fern.iamservice.domain.RolePermissionEntity;
import com.fern.iamservice.domain.RoleStatus;
import com.fern.iamservice.dto.CreateRoleRequest;
import com.fern.iamservice.dto.RoleResponse;
import com.fern.iamservice.repository.PermissionRepository;
import com.fern.iamservice.repository.RolePermissionRepository;
import com.fern.iamservice.repository.RoleRepository;
import com.fern.platform.common.ConflictException;
import java.time.Clock;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RoleService {
    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final PolicyVersionService policyVersionService;
    private final IamOutboxService outboxService;
    private final Clock clock;

    public RoleService(
            RoleRepository roleRepository,
            PermissionRepository permissionRepository,
            RolePermissionRepository rolePermissionRepository,
            PolicyVersionService policyVersionService,
            IamOutboxService outboxService,
            Clock clock
    ) {
        this.roleRepository = roleRepository;
        this.permissionRepository = permissionRepository;
        this.rolePermissionRepository = rolePermissionRepository;
        this.policyVersionService = policyVersionService;
        this.outboxService = outboxService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<RoleResponse> list() {
        return roleRepository.findAll().stream().map(this::toResponse).toList();
    }

    @Transactional
    public RoleResponse create(CreateRoleRequest request) {
        if (roleRepository.findByCode(request.code()).isPresent()) {
            throw new ConflictException("Role code already exists: " + request.code());
        }

        RoleEntity entity = new RoleEntity();
        entity.setCode(request.code());
        entity.setName(request.name());
        entity.setDescription(request.description());
        entity.setStatus(RoleStatus.ACTIVE);
        entity.setCreatedAt(clock.instant());
        entity.setUpdatedAt(clock.instant());
        roleRepository.save(entity);

        var permissions = permissionRepository.findAllByCodeIn(request.permissionCodes());
        if (permissions.size() != request.permissionCodes().size()) {
            throw new ConflictException("One or more permission codes do not exist");
        }
        permissions.forEach(permission -> {
            RolePermissionEntity assignment = new RolePermissionEntity();
            assignment.setRoleId(entity.getId());
            assignment.setPermissionId(permission.getId());
            assignment.setCreatedAt(clock.instant());
            rolePermissionRepository.save(assignment);
        });

        policyVersionService.bump();
        RoleResponse response = toResponse(entity);
        outboxService.enqueue("role", entity.getId().toString(), "iam.role.changed", entity.getId().toString(), response);
        return response;
    }

    private RoleResponse toResponse(RoleEntity entity) {
        List<Long> permissionIds = rolePermissionRepository.findAllByRoleId(entity.getId()).stream()
                .map(RolePermissionEntity::getPermissionId)
                .toList();
        Set<String> permissionCodes = new LinkedHashSet<>(permissionRepository.findAllById(permissionIds).stream()
                .map(permission -> permission.getCode())
                .collect(Collectors.toSet()));
        return new RoleResponse(entity.getId(), entity.getCode(), entity.getName(), entity.getDescription(), entity.getStatus(), permissionCodes);
    }
}
