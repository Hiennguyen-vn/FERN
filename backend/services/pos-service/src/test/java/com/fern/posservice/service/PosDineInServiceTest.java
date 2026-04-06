package com.fern.posservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fern.platform.common.ConflictException;
import com.fern.platform.common.OperationalShardAccess;
import com.fern.platform.common.OperationalShardRegistry;
import com.fern.platform.common.RouteKey;
import com.fern.platform.common.ShardId;
import com.fern.platform.common.ShardResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

class PosDineInServiceTest {
    @Mock
    private PosAuthorizer posAuthorizer;

    @Mock
    private PosOrgClient posOrgClient;

    @Mock
    private OperationalShardRegistry operationalShardRegistry;

    @Mock
    private ShardResolver shardResolver;

    @Mock
    private NamedParameterJdbcTemplate jdbcTemplate;

    @Mock
    private TransactionTemplate transactionTemplate;

    private PosDineInService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(shardResolver.resolve(any(RouteKey.class))).thenReturn(new ShardId("operational-0"));
        when(operationalShardRegistry.get(any(ShardId.class)))
                .thenReturn(new OperationalShardAccess(new ShardId("operational-0"), jdbcTemplate, transactionTemplate));
        service = new PosDineInService(posAuthorizer, posOrgClient, operationalShardRegistry, shardResolver);
    }

    @Test
    void shouldBindOutletIdWhenAssigningTableToOrder() {
        when(jdbcTemplate.update(anyString(), any(MapSqlParameterSource.class))).thenReturn(1);

        service.assignTableToOrder(11L, 22L, 1L, 101L);

        ArgumentCaptor<MapSqlParameterSource> paramsCaptor = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbcTemplate).update(anyString(), paramsCaptor.capture());
        assertThat(paramsCaptor.getValue().getValue("outletId")).isEqualTo(101L);
    }

    @Test
    void shouldRejectAssignWhenTableDoesNotBelongToOutlet() {
        when(jdbcTemplate.update(anyString(), any(MapSqlParameterSource.class))).thenReturn(0);

        assertThatThrownBy(() -> service.assignTableToOrder(11L, 22L, 1L, 101L))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Table does not belong to the outlet or is not available for assignment");
    }
}
