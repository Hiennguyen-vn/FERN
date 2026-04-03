package com.fern.posservice;

import static org.assertj.core.api.Assertions.assertThat;

import com.fern.platform.common.OperationalShardRegistry;
import com.fern.platform.common.ShardResolver;
import com.fern.posservice.service.PosOrderService;
import com.fern.posservice.service.PosSessionService;
import com.fern.posservice.service.PosStatsService;
import java.lang.reflect.Constructor;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

class PosShardReadinessArchitectureTest {

    @Test
    void servicesShouldUseShardInfrastructureInsteadOfDirectOperationalJdbcBeans() {
        assertNoDirectDependency(PosSessionService.class, NamedParameterJdbcTemplate.class);
        assertNoDirectDependency(PosSessionService.class, TransactionTemplate.class);
        assertHasDependency(PosSessionService.class, OperationalShardRegistry.class);
        assertHasDependency(PosSessionService.class, ShardResolver.class);

        assertNoDirectDependency(PosOrderService.class, NamedParameterJdbcTemplate.class);
        assertNoDirectDependency(PosOrderService.class, TransactionTemplate.class);
        assertHasDependency(PosOrderService.class, OperationalShardRegistry.class);
        assertHasDependency(PosOrderService.class, ShardResolver.class);

        assertNoDirectDependency(PosStatsService.class, NamedParameterJdbcTemplate.class);
        assertHasDependency(PosStatsService.class, OperationalShardRegistry.class);
        assertHasDependency(PosStatsService.class, ShardResolver.class);
    }

    private void assertNoDirectDependency(Class<?> type, Class<?> forbidden) {
        Constructor<?> constructor = type.getDeclaredConstructors()[0];
        assertThat(Arrays.stream(type.getDeclaredFields()).noneMatch(field -> field.getType().equals(forbidden))).isTrue();
        assertThat(Arrays.stream(constructor.getParameterTypes()).noneMatch(forbidden::equals)).isTrue();
    }

    private void assertHasDependency(Class<?> type, Class<?> dependency) {
        Constructor<?> constructor = type.getDeclaredConstructors()[0];
        assertThat(Arrays.stream(constructor.getParameterTypes()).anyMatch(dependency::equals)).isTrue();
    }
}
