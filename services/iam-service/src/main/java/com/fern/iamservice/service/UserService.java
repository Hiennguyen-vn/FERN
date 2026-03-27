package com.fern.iamservice.service;

import com.fern.iamservice.domain.UserAccountEntity;
import com.fern.iamservice.domain.RoleStatus;
import com.fern.iamservice.domain.UserRoleAssignmentEntity;
import com.fern.iamservice.domain.UserScopeAssignmentEntity;
import com.fern.iamservice.domain.UserStatus;
import com.fern.iamservice.dto.AssignUserRolesRequest;
import com.fern.iamservice.dto.AssignUserScopesRequest;
import com.fern.iamservice.dto.CreateUserRequest;
import com.fern.iamservice.dto.UpdateUserRequest;
import com.fern.iamservice.dto.UserResponse;
import com.fern.iamservice.repository.RoleRepository;
import com.fern.iamservice.repository.UserAccountRepository;
import com.fern.iamservice.repository.UserRoleAssignmentRepository;
import com.fern.iamservice.repository.UserScopeAssignmentRepository;
import com.fern.platform.common.ConflictException;
import com.fern.platform.common.FernPrincipal;
import com.fern.platform.common.ScopeType;
import com.fern.platform.security.FernPasswordHasher;
import java.time.Clock;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {
    private final UserAccountRepository userAccountRepository;
    private final RoleRepository roleRepository;
    private final UserRoleAssignmentRepository userRoleAssignmentRepository;
    private final UserScopeAssignmentRepository userScopeAssignmentRepository;
    private final FernPasswordHasher passwordHasher;
    private final UserViewService userViewService;
    private final PolicyVersionService policyVersionService;
    private final ScopeVersionBridgeService scopeVersionBridgeService;
    private final IamOutboxService outboxService;
    private final IamAuditService iamAuditService;
    private final RefreshTokenService refreshTokenService;
    private final Clock clock;

    public UserService(
            UserAccountRepository userAccountRepository,
            RoleRepository roleRepository,
            UserRoleAssignmentRepository userRoleAssignmentRepository,
            UserScopeAssignmentRepository userScopeAssignmentRepository,
            FernPasswordHasher passwordHasher,
            UserViewService userViewService,
            PolicyVersionService policyVersionService,
            ScopeVersionBridgeService scopeVersionBridgeService,
            IamOutboxService outboxService,
            IamAuditService iamAuditService,
            RefreshTokenService refreshTokenService,
            Clock clock
    ) {
        this.userAccountRepository = userAccountRepository;
        this.roleRepository = roleRepository;
        this.userRoleAssignmentRepository = userRoleAssignmentRepository;
        this.userScopeAssignmentRepository = userScopeAssignmentRepository;
        this.passwordHasher = passwordHasher;
        this.userViewService = userViewService;
        this.policyVersionService = policyVersionService;
        this.scopeVersionBridgeService = scopeVersionBridgeService;
        this.outboxService = outboxService;
        this.iamAuditService = iamAuditService;
        this.refreshTokenService = refreshTokenService;
        this.clock = clock;
    }

    @Transactional
    public UserResponse create(FernPrincipal principal, CreateUserRequest request) {
        if (userAccountRepository.findByUsername(request.username()).isPresent()) {
            throw new ConflictException("Username already exists: " + request.username());
        }

        UserAccountEntity user = new UserAccountEntity();
        user.setUsername(request.username());
        user.setPasswordHash(passwordHasher.encode(request.password()));
        user.setFullName(request.fullName());
        user.setEmail(request.email());
        user.setPhone(request.phone());
        user.setStatus(request.status() == null ? UserStatus.ACTIVE : request.status());
        user.setPasswordChangedAt(clock.instant());
        user.setCreatedAt(clock.instant());
        user.setUpdatedAt(clock.instant());
        userAccountRepository.save(user);

        UserResponse response = userViewService.toResponse(user);
        outboxService.enqueue("user", user.getId().toString(), "iam.user.changed", user.getId().toString(), response);
        iamAuditService.publishAuditEvent(
                "iam.user.created",
                principal,
                user.getId(),
                "CREATE_USER",
                "user_account",
                String.valueOf(user.getId()),
                "SUCCESS",
                null,
                response,
                java.util.Map.of("username", user.getUsername())
        );
        return response;
    }

    @Transactional(readOnly = true)
    public UserResponse get(Long id) {
        return userViewService.toResponse(userViewService.findUser(id));
    }

    @Transactional
    public UserResponse update(FernPrincipal principal, Long id, UpdateUserRequest request) {
        UserAccountEntity user = userViewService.findUser(id);
        UserResponse before = userViewService.toResponse(user);

        boolean fullNameChanged = request.fullName() != null && !Objects.equals(request.fullName(), user.getFullName());
        boolean emailChanged = request.email() != null && !Objects.equals(request.email(), user.getEmail());
        boolean phoneChanged = request.phone() != null && !Objects.equals(request.phone(), user.getPhone());
        boolean statusChanged = request.status() != null && request.status() != user.getStatus();
        boolean profileChanged = fullNameChanged || emailChanged || phoneChanged;
        if (!profileChanged && !statusChanged) {
            return before;
        }

        if (fullNameChanged) {
            user.setFullName(request.fullName());
        }
        if (emailChanged) {
            user.setEmail(request.email());
        }
        if (phoneChanged) {
            user.setPhone(request.phone());
        }
        if (statusChanged) {
            user.setStatus(request.status());
            policyVersionService.bump();
            refreshTokenService.revokeAllByUserId(id);
        }
        user.setUpdatedAt(clock.instant());

        UserResponse response = userViewService.toResponse(user);
        if (statusChanged) {
            outboxService.enqueue("user", user.getId().toString(), "iam.user.status-changed", user.getId().toString(), response);
            iamAuditService.userStatusChanged(principal, user, before, response);
        } else {
            outboxService.enqueue("user", user.getId().toString(), "iam.user.changed", user.getId().toString(), response);
            iamAuditService.userUpdated(principal, user, before, response);
        }
        return response;
    }

    @Transactional
    public UserResponse assignRoles(FernPrincipal principal, Long id, AssignUserRolesRequest request) {
        UserAccountEntity user = userViewService.findUser(id);
        var roles = roleRepository.findAllByCodeIn(request.roleCodes());
        if (roles.size() != request.roleCodes().size()) {
            throw new ConflictException("One or more role codes do not exist");
        }
        if (roles.stream().anyMatch(role -> role.getStatus() != RoleStatus.ACTIVE)) {
            throw new ConflictException("One or more role codes are inactive");
        }

        userRoleAssignmentRepository.deleteByUserId(id);
        roles.forEach(role -> {
            UserRoleAssignmentEntity assignment = new UserRoleAssignmentEntity();
            assignment.setUserId(user.getId());
            assignment.setRoleId(role.getId());
            assignment.setCreatedAt(clock.instant());
            userRoleAssignmentRepository.save(assignment);
        });

        policyVersionService.bump();
        UserResponse response = userViewService.toResponse(user);
        outboxService.enqueue("user", user.getId().toString(), "iam.user.changed", user.getId().toString(), response);
        iamAuditService.userAccessChanged(principal, id, "ASSIGN_ROLES", response);
        return response;
    }

    @Transactional
    public UserResponse assignScopes(FernPrincipal principal, Long id, AssignUserScopesRequest request) {
        UserAccountEntity user = userViewService.findUser(id);
        userScopeAssignmentRepository.deleteByUserId(id);
        if (Boolean.TRUE.equals(request.system())) {
            saveScopeAssignment(user.getId(), ScopeType.SYSTEM, null);
        }
        if (request.regionIds() != null) {
            request.regionIds().stream().distinct().forEach(regionId -> saveScopeAssignment(user.getId(), ScopeType.REGION, regionId));
        }
        if (request.outletIds() != null) {
            request.outletIds().stream().distinct().forEach(outletId -> saveScopeAssignment(user.getId(), ScopeType.OUTLET, outletId));
        }

        scopeVersionBridgeService.bump();
        UserResponse response = userViewService.toResponse(user);
        outboxService.enqueue("user", user.getId().toString(), "iam.scope.changed", user.getId().toString(), response);
        iamAuditService.userAccessChanged(principal, id, "ASSIGN_SCOPES", response);
        return response;
    }

    private void saveScopeAssignment(Long userId, ScopeType scopeType, Long scopeId) {
        UserScopeAssignmentEntity assignment = new UserScopeAssignmentEntity();
        assignment.setUserId(userId);
        assignment.setScopeType(scopeType);
        assignment.setScopeId(scopeId);
        assignment.setCreatedAt(clock.instant());
        userScopeAssignmentRepository.save(assignment);
    }
}
