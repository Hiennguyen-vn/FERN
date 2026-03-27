package com.fern.orgservice.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fern.orgservice.dto.ExpandedScopeResponse;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class ScopeExpansionService {
    private final StringRedisTemplate redisTemplate;
    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final RegionClosureRepository closureRepository;
    private final ScopeVersionService scopeVersionService;
    private final ObjectMapper objectMapper;

    public ScopeExpansionService(
            StringRedisTemplate redisTemplate,
            NamedParameterJdbcTemplate jdbcTemplate,
            RegionClosureRepository closureRepository,
            ScopeVersionService scopeVersionService,
            ObjectMapper objectMapper
    ) {
        this.redisTemplate = redisTemplate;
        this.jdbcTemplate = jdbcTemplate;
        this.closureRepository = closureRepository;
        this.scopeVersionService = scopeVersionService;
        this.objectMapper = objectMapper;
    }

    public ExpandedScopeResponse expand(List<Long> regionIds, List<Long> outletIds) {
        List<Long> normalizedRegionIds = regionIds == null ? List.of() : regionIds.stream().distinct().sorted().toList();
        List<Long> normalizedOutletIds = outletIds == null ? List.of() : outletIds.stream().distinct().sorted().toList();
        long scopeVersion = scopeVersionService.currentVersion();
        String cacheKey = "fern:org:scope-expansion:" + scopeVersion + ":" + normalizedRegionIds + ":" + normalizedOutletIds;

        String cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            try {
                return objectMapper.readValue(cached, ExpandedScopeResponse.class);
            } catch (JsonProcessingException ignored) {
                redisTemplate.delete(cacheKey);
            }
        }

        LinkedHashSet<Long> expandedRegions = new LinkedHashSet<>(closureRepository.expandRegionIds(normalizedRegionIds));
        LinkedHashSet<Long> expandedOutlets = new LinkedHashSet<>(normalizedOutletIds);
        if (!expandedRegions.isEmpty()) {
            expandedOutlets.addAll(jdbcTemplate.queryForList("""
                    SELECT id
                    FROM org.outlet
                    WHERE region_id IN (:regionIds) AND deleted_at IS NULL
                    ORDER BY id
                    """, new MapSqlParameterSource("regionIds", expandedRegions.stream().toList()), Long.class));
        }

        ExpandedScopeResponse response = new ExpandedScopeResponse(
                expandedRegions.stream().toList(),
                expandedOutlets.stream().sorted().collect(Collectors.toList()),
                scopeVersion
        );
        try {
            redisTemplate.opsForValue().set(cacheKey, objectMapper.writeValueAsString(response), Duration.ofMinutes(10));
        } catch (JsonProcessingException ignored) {
        }
        return response;
    }
}
