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
        jdbcTemplate.queryForObject(
                "SELECT pg_advisory_xact_lock(hashtext(:lockKey))",
                Map.of("lockKey", "catalog:" + key),
                Long.class
        );
    }
}
