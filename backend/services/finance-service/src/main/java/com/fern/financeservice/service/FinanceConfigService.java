package com.fern.financeservice.service;

import static com.fern.financeservice.service.FinanceJdbcSupport.nullableLong;
import static com.fern.financeservice.service.FinanceJdbcSupport.params;
import static com.fern.financeservice.service.FinancePrincipalSupport.actorId;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fern.financeservice.dto.FinanceCommands.PutNumberingRuleRequest;
import com.fern.financeservice.dto.FinanceCommands.PutSystemPolicyRequest;
import com.fern.financeservice.dto.FinanceResponses.NumberingRuleResponse;
import com.fern.financeservice.dto.FinanceResponses.SystemPolicyResponse;
import com.fern.financeservice.service.payroll.model.DocumentNumberAllocation;
import com.fern.platform.common.FernPrincipal;
import com.fern.platform.common.PermissionCodes;
import com.fern.platform.common.ResourceNotFoundException;
import com.fern.platform.common.SnowflakeIdGenerator;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FinanceConfigService {
    private final NamedParameterJdbcTemplate masterJdbcTemplate;
    private final FinanceAuthorizer financeAuthorizer;
    private final ObjectMapper objectMapper;
    private final SnowflakeIdGenerator snowflakeIdGenerator;

    public FinanceConfigService(
            @Qualifier("masterJdbcTemplate") NamedParameterJdbcTemplate masterJdbcTemplate,
            FinanceAuthorizer financeAuthorizer,
            ObjectMapper objectMapper,
            SnowflakeIdGenerator snowflakeIdGenerator
    ) {
        this.masterJdbcTemplate = masterJdbcTemplate;
        this.financeAuthorizer = financeAuthorizer;
        this.objectMapper = objectMapper;
        this.snowflakeIdGenerator = snowflakeIdGenerator;
    }

    @Transactional("masterTransactionManager")
    public NumberingRuleResponse putNumberingRule(FernPrincipal principal, String documentType, PutNumberingRuleRequest request) {
        financeAuthorizer.requireSystemPermission(principal, PermissionCodes.FINANCE_CONFIG_WRITE);
        masterJdbcTemplate.update("""
                INSERT INTO config.document_numbering_rule (
                    document_type, prefix, region_id, outlet_id, next_number, reset_period, format_pattern, is_active, updated_at
                ) VALUES (
                    :documentType, :prefix, :regionId, :outletId, :nextNumber, :resetPeriod, :formatPattern, :active, CURRENT_TIMESTAMP
                )
                ON CONFLICT (document_type) DO UPDATE
                SET prefix = EXCLUDED.prefix,
                    region_id = EXCLUDED.region_id,
                    outlet_id = EXCLUDED.outlet_id,
                    next_number = EXCLUDED.next_number,
                    reset_period = EXCLUDED.reset_period,
                    format_pattern = EXCLUDED.format_pattern,
                    is_active = EXCLUDED.is_active,
                    updated_at = CURRENT_TIMESTAMP
                """, params(
                "documentType", documentType,
                "prefix", request.prefix(),
                "regionId", request.regionId(),
                "outletId", request.outletId(),
                "nextNumber", request.nextNumber() == null ? 1L : request.nextNumber(),
                "resetPeriod", request.resetPeriod() == null ? "NEVER" : request.resetPeriod(),
                "formatPattern", request.formatPattern(),
                "active", request.active() == null ? Boolean.TRUE : request.active()
        ));
        return getNumberingRule(principal, documentType);
    }

    public NumberingRuleResponse getNumberingRule(FernPrincipal principal, String documentType) {
        financeAuthorizer.requireSystemPermission(principal, PermissionCodes.FINANCE_CONFIG_READ);
        NumberingRuleResponse response = masterJdbcTemplate.query("""
                SELECT id, document_type, prefix, region_id, outlet_id, next_number, reset_period, format_pattern, is_active
                FROM config.document_numbering_rule
                WHERE document_type = :documentType
                """, params("documentType", documentType), rs -> rs.next() ? new NumberingRuleResponse(
                rs.getLong("id"),
                rs.getString("document_type"),
                rs.getString("prefix"),
                nullableLong(rs, "region_id"),
                nullableLong(rs, "outlet_id"),
                rs.getLong("next_number"),
                rs.getString("reset_period"),
                rs.getString("format_pattern"),
                rs.getBoolean("is_active")
        ) : null);
        if (response == null) {
            throw new ResourceNotFoundException("Numbering rule not found");
        }
        return response;
    }

    @Transactional("masterTransactionManager")
    public SystemPolicyResponse putSystemPolicy(FernPrincipal principal, String policyKey, PutSystemPolicyRequest request) {
        financeAuthorizer.requireSystemPermission(principal, PermissionCodes.FINANCE_CONFIG_WRITE);
        masterJdbcTemplate.update("""
                INSERT INTO config.system_policy (
                    policy_key, policy_value, description, updated_by_user_id, updated_at
                ) VALUES (
                    :policyKey, CAST(:policyValue AS jsonb), :description, :updatedByUserId, CURRENT_TIMESTAMP
                )
                ON CONFLICT (policy_key) DO UPDATE
                SET policy_value = EXCLUDED.policy_value,
                    description = EXCLUDED.description,
                    updated_by_user_id = EXCLUDED.updated_by_user_id,
                    updated_at = CURRENT_TIMESTAMP
                """, params(
                "policyKey", policyKey,
                "policyValue", request.policyValue().toString(),
                "description", request.description(),
                "updatedByUserId", actorId(principal)
        ));
        return getSystemPolicy(principal, policyKey);
    }

    public SystemPolicyResponse getSystemPolicy(FernPrincipal principal, String policyKey) {
        financeAuthorizer.requireSystemPermission(principal, PermissionCodes.FINANCE_CONFIG_READ);
        SystemPolicyResponse response = masterJdbcTemplate.query("""
                SELECT policy_key, policy_value::text AS policy_value, description
                FROM config.system_policy
                WHERE policy_key = :policyKey
                """, params("policyKey", policyKey), rs -> rs.next() ? new SystemPolicyResponse(
                rs.getString("policy_key"),
                readTree(rs.getString("policy_value")),
                rs.getString("description")
        ) : null);
        if (response == null) {
            throw new ResourceNotFoundException("System policy not found");
        }
        return response;
    }

    public String nextDocumentNumber(String documentType) {
        DocumentNumberAllocation allocation = masterJdbcTemplate.query("""
                UPDATE config.document_numbering_rule
                SET next_number = next_number + 1,
                    updated_at = CURRENT_TIMESTAMP
                WHERE document_type = :documentType
                  AND is_active = TRUE
                RETURNING prefix, next_number - 1 AS allocated_number
                """, params("documentType", documentType), rs -> rs.next() ? new DocumentNumberAllocation(
                rs.getString("prefix"),
                rs.getLong("allocated_number")
        ) : null);
        if (allocation == null) {
            return documentType + "-" + snowflakeIdGenerator.nextId();
        }
        String prefix = allocation.prefix() == null ? documentType : allocation.prefix();
        return prefix + "-" + String.format("%06d", allocation.allocatedNumber());
    }

    public JsonNode readPolicyValue(String policyKey) {
        try {
            return masterJdbcTemplate.query("""
                    SELECT policy_value::text AS policy_value
                    FROM config.system_policy
                    WHERE policy_key = :policyKey
                    """, params("policyKey", policyKey), rs -> rs.next() ? readTree(rs.getString("policy_value")) : objectMapper.createObjectNode());
        } catch (Exception exception) {
            return objectMapper.createObjectNode();
        }
    }

    private JsonNode readTree(String value) {
        try {
            return value == null ? objectMapper.createObjectNode() : objectMapper.readTree(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to parse JSON value", exception);
        }
    }
}
