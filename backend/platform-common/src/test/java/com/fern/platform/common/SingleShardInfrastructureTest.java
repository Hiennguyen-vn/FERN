package com.fern.platform.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

class SingleShardInfrastructureTest {

    @Test
    void shouldResolveAnyRouteToConfiguredDefaultShard() {
        ShardId defaultShard = new ShardId("operational-0");
        SingleShardResolver resolver = new SingleShardResolver(defaultShard);

        assertThat(resolver.resolve(RouteKey.of(10L, 20L))).isEqualTo(defaultShard);
        assertThat(resolver.resolve(RouteKey.of(null, null))).isEqualTo(defaultShard);
    }

    @Test
    void shouldReturnConfiguredAccessForDefaultShard() {
        OperationalShardAccess access = new OperationalShardAccess(
                new ShardId("operational-0"),
                mock(NamedParameterJdbcTemplate.class),
                mock(TransactionTemplate.class)
        );
        SingleOperationalShardRegistry registry = new SingleOperationalShardRegistry(access);

        assertThat(registry.get(new ShardId("operational-0"))).isSameAs(access);
    }

    @Test
    void shouldRejectUnknownShardId() {
        OperationalShardAccess access = new OperationalShardAccess(
                new ShardId("operational-0"),
                mock(NamedParameterJdbcTemplate.class),
                mock(TransactionTemplate.class)
        );
        SingleOperationalShardRegistry registry = new SingleOperationalShardRegistry(access);

        assertThatThrownBy(() -> registry.get(new ShardId("operational-1")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("operational-1");
    }
}
