package com.fern.inventoryservice;

import static org.assertj.core.api.Assertions.assertThat;

import com.fern.inventoryservice.service.StockAdjustmentService;
import com.fern.inventoryservice.service.StockCountService;
import com.fern.inventoryservice.service.WasteRecordService;
import com.fern.platform.common.OperationalShardRegistry;
import com.fern.platform.common.ShardResolver;
import java.lang.reflect.Constructor;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

class InventoryShardReadinessArchitectureTest {

    @Test
    void writeHeavyServicesShouldUseShardInfrastructureInsteadOfDirectOperationalJdbcBeans() {
        assertNoDirectDependency(StockAdjustmentService.class, NamedParameterJdbcTemplate.class);
        assertHasDependency(StockAdjustmentService.class, OperationalShardRegistry.class);
        assertHasDependency(StockAdjustmentService.class, ShardResolver.class);

        assertNoDirectDependency(StockCountService.class, NamedParameterJdbcTemplate.class);
        assertHasDependency(StockCountService.class, OperationalShardRegistry.class);
        assertHasDependency(StockCountService.class, ShardResolver.class);

        assertNoDirectDependency(WasteRecordService.class, NamedParameterJdbcTemplate.class);
        assertHasDependency(WasteRecordService.class, OperationalShardRegistry.class);
        assertHasDependency(WasteRecordService.class, ShardResolver.class);
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
