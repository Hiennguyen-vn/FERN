package com.fern.orgservice.service;

import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class RegionClosureRepository {
    private final NamedParameterJdbcTemplate jdbcTemplate;

    public RegionClosureRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void rebuild() {
        jdbcTemplate.getJdbcTemplate().execute("DELETE FROM org.region_closure");
        jdbcTemplate.getJdbcTemplate().execute("""
                INSERT INTO org.region_closure (ancestor_region_id, descendant_region_id, depth)
                WITH RECURSIVE closure AS (
                    SELECT id AS ancestor_region_id, id AS descendant_region_id, 0 AS depth
                    FROM org.region
                    UNION ALL
                    SELECT closure.ancestor_region_id, region.id, closure.depth + 1
                    FROM closure
                    JOIN org.region ON region.parent_region_id = closure.descendant_region_id
                )
                SELECT ancestor_region_id, descendant_region_id, depth
                FROM closure
                """);
    }

    public boolean isDescendant(Long ancestorId, Long descendantId) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM org.region_closure
                WHERE ancestor_region_id = :ancestorId AND descendant_region_id = :descendantId
                """, Map.of("ancestorId", ancestorId, "descendantId", descendantId), Integer.class);
        return count != null && count > 0;
    }

    public List<Long> expandRegionIds(List<Long> regionIds) {
        if (regionIds.isEmpty()) {
            return List.of();
        }
        return jdbcTemplate.queryForList("""
                SELECT DISTINCT descendant_region_id
                FROM org.region_closure
                WHERE ancestor_region_id IN (:regionIds)
                ORDER BY descendant_region_id
                """, new MapSqlParameterSource("regionIds", regionIds), Long.class);
    }
}
