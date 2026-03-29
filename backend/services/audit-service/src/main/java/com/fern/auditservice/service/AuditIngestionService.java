package com.fern.auditservice.service;

import com.fern.auditservice.repository.AuditJdbcRepository;
import com.fern.platform.audit.AuditEvent;
import com.fern.platform.audit.RequestTraceEvent;
import com.fern.platform.audit.SecurityEvent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class AuditIngestionService {
    private final AuditJdbcRepository auditJdbcRepository;

    public AuditIngestionService(AuditJdbcRepository auditJdbcRepository) {
        this.auditJdbcRepository = auditJdbcRepository;
    }

    public void ingestAuditEvent(AuditEvent event) {
        auditJdbcRepository.insertAuditEvent(event);
    }

    public void ingestSecurityEvent(SecurityEvent event) {
        auditJdbcRepository.insertSecurityEvent(event);
    }

    public void ingestRequestTrace(RequestTraceEvent event) {
        auditJdbcRepository.insertRequestTrace(event);
    }
}
