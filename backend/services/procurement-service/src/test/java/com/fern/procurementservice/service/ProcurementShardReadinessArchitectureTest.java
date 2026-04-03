package com.fern.procurementservice.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fern.platform.common.OperationalShardRegistry;
import com.fern.platform.common.ShardResolver;
import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Parameter;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

class ProcurementShardReadinessArchitectureTest {

    @Test
    void businessServicesShouldAvoidDirectOperationalJdbcInjection() {
        assertNoDirectDependency(PurchaseFlowService.class, NamedParameterJdbcTemplate.class);
        assertNoDirectDependency(PayablesService.class, NamedParameterJdbcTemplate.class);
        assertNoDirectDependency(ProcurementEventPublisher.class, NamedParameterJdbcTemplate.class);
    }

    @Test
    void repositoryShouldResolveOperationalAccessViaShardInfrastructure() {
        Constructor<?> constructor = ProcurementJdbcRepository.class.getDeclaredConstructors()[0];
        assertThat(Arrays.stream(constructor.getParameterTypes()).anyMatch(OperationalShardRegistry.class::equals)).isTrue();
        assertThat(Arrays.stream(constructor.getParameterTypes()).anyMatch(ShardResolver.class::equals)).isTrue();

        long jdbcTemplateParameters = Arrays.stream(constructor.getParameters())
                .filter(parameter -> parameter.getType().equals(NamedParameterJdbcTemplate.class))
                .count();
        assertThat(jdbcTemplateParameters).isEqualTo(1);

        Parameter masterJdbcParameter = Arrays.stream(constructor.getParameters())
                .filter(parameter -> parameter.getType().equals(NamedParameterJdbcTemplate.class))
                .findFirst()
                .orElseThrow();
        Qualifier qualifier = qualifier(masterJdbcParameter);
        assertThat(qualifier).isNotNull();
        assertThat(qualifier.value()).isEqualTo("masterJdbcTemplate");
    }

    private void assertNoDirectDependency(Class<?> type, Class<?> forbidden) {
        Constructor<?> constructor = type.getDeclaredConstructors()[0];
        assertThat(Arrays.stream(constructor.getParameterTypes()).noneMatch(forbidden::equals)).isTrue();
    }

    private Qualifier qualifier(Parameter parameter) {
        for (Annotation annotation : parameter.getAnnotations()) {
            if (annotation instanceof Qualifier qualifier) {
                return qualifier;
            }
        }
        return null;
    }
}
