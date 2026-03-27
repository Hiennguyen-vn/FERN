package com.fern.iamservice.service;

import com.fern.iamservice.dto.PermissionResponse;
import com.fern.iamservice.repository.PermissionRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PermissionService {
    private final PermissionRepository permissionRepository;

    public PermissionService(PermissionRepository permissionRepository) {
        this.permissionRepository = permissionRepository;
    }

    @Transactional(readOnly = true)
    public List<PermissionResponse> list() {
        return permissionRepository.findAll().stream()
                .map(permission -> new PermissionResponse(permission.getCode(), permission.getName(), permission.getDescription()))
                .toList();
    }
}
