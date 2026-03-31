package com.fern.catalogservice.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.mock;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.PreparedStatementCallback;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

class CatalogConcurrencyGuardTest {
    @Test
    void shouldAcquireAdvisoryLockByExecutingStatement() throws Exception {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        CatalogConcurrencyGuard guard = new CatalogConcurrencyGuard(jdbcTemplate);

        guard.lock("recipe-version:recipe:42");

        verify(jdbcTemplate).execute(
                eq("SELECT pg_advisory_xact_lock(hashtext(:lockKey))"),
                eq(Map.of("lockKey", "catalog:recipe-version:recipe:42")),
                any(PreparedStatementCallback.class)
        );
        verifyNoMoreInteractions(jdbcTemplate);
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldConsumeResultSetWhenAcquiringAdvisoryLock() throws Exception {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        PreparedStatement preparedStatement = mock(PreparedStatement.class);
        ResultSet resultSet = mock(ResultSet.class);
        var callbackCaptor = new PreparedStatementCallback<?>[1];

        org.mockito.Mockito.when(jdbcTemplate.execute(
                        eq("SELECT pg_advisory_xact_lock(hashtext(:lockKey))"),
                        eq(Map.of("lockKey", "catalog:recipe-version:recipe:42")),
                        any(PreparedStatementCallback.class)))
                .thenAnswer(invocation -> {
                    callbackCaptor[0] = invocation.getArgument(2);
                    return null;
                });
        org.mockito.Mockito.when(preparedStatement.executeQuery()).thenReturn(resultSet);
        org.mockito.Mockito.when(resultSet.next()).thenReturn(true, false);

        CatalogConcurrencyGuard guard = new CatalogConcurrencyGuard(jdbcTemplate);
        guard.lock("recipe-version:recipe:42");

        callbackCaptor[0].doInPreparedStatement(preparedStatement);

        verify(preparedStatement).executeQuery();
        verify(resultSet, times(2)).next();
        verify(resultSet).close();
    }
}
