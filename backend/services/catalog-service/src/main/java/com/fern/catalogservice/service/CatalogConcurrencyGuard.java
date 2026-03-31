package com.fern.catalogservice.service;

import java.util.Map;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

@Component
class CatalogConcurrencyGuard {
    private final NamedParameterJdbcTemplate jdbcTemplate;

    CatalogConcurrencyGuard(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    void lock(String key) {
        jdbcTemplate.execute(
                "SELECT pg_advisory_xact_lock(hashtext(:lockKey))",
                Map.of("lockKey", "catalog:" + key),
                preparedStatement -> {
                    try (var resultSet = preparedStatement.executeQuery()) {
                        while (resultSet.next()) {
                            // Consume the advisory-lock result row so the statement completes on all drivers.
                        }
                    }
                    return null;
                }
        );
    }
}
