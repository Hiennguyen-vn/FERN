package com.fern.iamservice.service;

import com.fern.iamservice.domain.PermissionEntity;
import com.fern.iamservice.domain.PermissionOverrideMode;
import com.fern.iamservice.domain.RoleEntity;
import com.fern.iamservice.domain.RoleStatus;
import com.fern.iamservice.domain.UserPermissionOverrideEntity;
import com.fern.iamservice.dto.EffectiveAccessResponse;
import com.fern.iamservice.domain.UserAccountEntity;
import com.fern.iamservice.dto.UserResponse;
import com.fern.iamservice.repository.PermissionRepository;
import com.fern.iamservice.repository.RolePermissionRepository;
import com.fern.iamservice.repository.RoleRepository;
import com.fern.iamservice.repository.UserAccountRepository;
import com.fern.iamservice.repository.UserPermissionOverrideRepository;
import com.fern.iamservice.repository.UserRoleAssignmentRepository;
import com.fern.iamservice.repository.UserScopeAssignmentRepository;
import com.fern.platform.common.ResourceNotFoundException;
import com.fern.platform.common.ScopeRoots;
import com.fern.platform.common.ScopeType;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserViewService {
    private final UserAccountRepository userAccountRepository;
    private final UserRoleAssignmentRepository userRoleAssignmentRepository;
    private final RoleRepository roleRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final PermissionRepository permissionRepository;
    private final UserScopeAssignmentRepository userScopeAssignmentRepository;
    private final UserPermissionOverrideRepository userPermissionOverrideRepository;
    private final Clock clock;

    public UserViewService(
            UserAccountRepository userAccountRepository,
            UserRoleAssignmentRepository userRoleAssignmentRepository,
            RoleRepository roleRepository,
            RolePermissionRepository rolePermissionRepository,
            PermissionRepository permissionRepository,
            UserScopeAssignmentRepository userScopeAssignmentRepository,
            UserPermissionOverrideRepository userPermissionOverrideRepository,
            Clock clock
    ) {
        this.userAccountRepository = userAccountRepository;
        this.userRoleAssignmentRepository = userRoleAssignmentRepository;
        this.roleRepository = roleRepository;
        this.rolePermissionRepository = rolePermissionRepository;
        this.permissionRepository = permissionRepository;
        this.userScopeAssignmentRepository = userScopeAssignmentRepository;
        this.userPermissionOverrideRepository = userPermissionOverrideRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public UserAccountEntity findUser(Long id) {
        return userAccountRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + id));
    }

    @Transactional(readOnly = true)
    public Set<String> roleCodes(Long userId) {
        var roleIds = userRoleAssignmentRepository.findAllByUserId(userId).stream().map(assignment -> assignment.getRoleId()).toList();
        return new LinkedHashSet<>(activeRolesById(roleIds).values().stream().map(RoleEntity::getCode).toList());
    }

    @Transactional(readOnly = true)
    public Set<String> permissionCodes(Long userId) {
        return effectiveAccess(userId).effectivePermissions();
    }

    @Transactional(readOnly = true)
    public ScopeRoots scopeRoots(Long userId) {
        var assignments = userScopeAssignmentRepository.findAllByUserId(userId);
        boolean system = assignments.stream().anyMatch(assignment -> assignment.getScopeType() == ScopeType.SYSTEM);
        List<Long> regions = assignments.stream()
                .filter(assignment -> assignment.getScopeType() == ScopeType.REGION)
                .map(assignment -> assignment.getScopeId())
                .distinct()
                .sorted()
                .toList();
        List<Long> outlets = assignments.stream()
                .filter(assignment -> assignment.getScopeType() == ScopeType.OUTLET)
                .map(assignment -> assignment.getScopeId())
                .distinct()
                .sorted()
                .toList();
        return new ScopeRoots(system, regions, outlets);
    }

    @Transactional(readOnly = true)
    public UserResponse toResponse(UserAccountEntity user) {
        return new UserResponse(
                user.getId(),
                user.getUsername(),
                user.getFullName(),
                user.getEmail(),
                user.getPhone(),
                user.getStatus(),
                roleCodes(user.getId()),
                scopeRoots(user.getId())
        );
    }

    @Transactional(readOnly = true)
    public EffectiveAccessResponse effectiveAccess(Long userId) {
        Set<String> roleCodes = roleCodes(userId);
        ScopeRoots scopeRoots = scopeRoots(userId);
        Map<String, LinkedHashSet<String>> sources = new LinkedHashMap<>();

        var roleAssignments = userRoleAssignmentRepository.findAllByUserId(userId);
        Map<Long, RoleEntity> activeRolesById = activeRolesById(roleAssignments.stream()
                .map(assignment -> assignment.getRoleId())
                .toList());
        Map<Long, String> roleCodeById = activeRolesById.values().stream()
                .collect(Collectors.toMap(RoleEntity::getId, RoleEntity::getCode));

        Set<String> rolePermissionCodes = new LinkedHashSet<>();
        if (!activeRolesById.isEmpty()) {
            var rolePermissionAssignments = rolePermissionRepository.findAllByRoleIdIn(activeRolesById.keySet());
            Map<Long, String> permissionCodeById = permissionRepository.findAllById(rolePermissionAssignments.stream()
                            .map(assignment -> assignment.getPermissionId())
                            .collect(Collectors.toSet())).stream()
                    .collect(Collectors.toMap(PermissionEntity::getId, PermissionEntity::getCode));
            rolePermissionAssignments.forEach(assignment -> {
                String roleCode = roleCodeById.get(assignment.getRoleId());
                String permissionCode = permissionCodeById.get(assignment.getPermissionId());
                if (roleCode != null && permissionCode != null) {
                    rolePermissionCodes.add(permissionCode);
                    sources.computeIfAbsent(permissionCode, ignored -> new LinkedHashSet<>())
                            .add("ROLE:" + roleCode);
                }
            });
        }

        Set<String> directGrants = new LinkedHashSet<>();
        Set<String> directDenies = new LinkedHashSet<>();
        List<UserPermissionOverrideEntity> activeOverrides = activeOverrides(userId);
        if (!activeOverrides.isEmpty()) {
            Map<Long, String> permissionCodeById = permissionRepository.findAllById(activeOverrides.stream()
                            .map(UserPermissionOverrideEntity::getPermissionId)
                            .collect(Collectors.toSet())).stream()
                    .collect(Collectors.toMap(PermissionEntity::getId, PermissionEntity::getCode));
            activeOverrides.forEach(override -> {
                String permissionCode = permissionCodeById.get(override.getPermissionId());
                if (permissionCode == null) {
                    return;
                }
                if (override.getOverrideMode() == PermissionOverrideMode.GRANT) {
                    directGrants.add(permissionCode);
                } else {
                    directDenies.add(permissionCode);
                }
                sources.computeIfAbsent(permissionCode, ignored -> new LinkedHashSet<>())
                        .add("DIRECT_" + override.getOverrideMode().name());
            });
        }

        Set<String> granted = new LinkedHashSet<>(rolePermissionCodes);
        granted.addAll(directGrants);
        Set<String> effective = new LinkedHashSet<>(granted);
        effective.removeAll(directDenies);

        Map<String, List<String>> normalizedSources = sources.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> entry.getValue().stream().sorted().toList(),
                        (left, right) -> left,
                        LinkedHashMap::new
                ));

        return new EffectiveAccessResponse(
                userId,
                roleCodes,
                granted,
                directDenies,
                effective,
                scopeRoots,
                normalizedSources
        );
    }

    @Transactional(readOnly = true)
    public List<UserPermissionOverrideEntity> activeOverrides(Long userId) {
        return userPermissionOverrideRepository.findAllByUserId(userId).stream()
                .filter(override -> override.getExpiresAt() == null || override.getExpiresAt().isAfter(clock.instant()))
                .toList();
    }

    private Map<Long, RoleEntity> activeRolesById(List<Long> roleIds) {
        if (roleIds.isEmpty()) {
            return Map.of();
        }
        return roleRepository.findAllById(roleIds).stream()
                .filter(role -> role.getStatus() == RoleStatus.ACTIVE)
                .collect(Collectors.toMap(RoleEntity::getId, role -> role));
    }
}
