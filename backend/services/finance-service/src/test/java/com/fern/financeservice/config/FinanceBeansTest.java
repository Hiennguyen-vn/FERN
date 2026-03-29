package com.fern.financeservice.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

class FinanceBeansTest {
    private final FinanceBeans financeBeans = new FinanceBeans();

    @Test
    void shouldConfigureRestClientTimeouts() throws Exception {
        RestClient restClient = financeBeans.restClient();

        Object requestFactory = readField(restClient, "clientRequestFactory");

        assertThat(requestFactory).isInstanceOf(SimpleClientHttpRequestFactory.class);
        assertThat(readField(requestFactory, "connectTimeout")).isEqualTo(5000);
        assertThat(readField(requestFactory, "readTimeout")).isEqualTo(10000);
    }

    @Test
    @DisplayName("should bind master and projection datasources from distinct property prefixes")
    void shouldUseDistinctDatasourcePrefixes() throws Exception {
        ConfigurationProperties masterProperties = FinanceBeans.class
                .getDeclaredMethod("masterDataSourceProperties")
                .getAnnotation(ConfigurationProperties.class);
        ConfigurationProperties projectionProperties = FinanceBeans.class
                .getDeclaredMethod("projectionDataSourceProperties")
                .getAnnotation(ConfigurationProperties.class);

        assertThat(masterProperties).isNotNull();
        assertThat(masterProperties.value()).isEqualTo("fern.master-datasource");
        assertThat(projectionProperties).isNotNull();
        assertThat(projectionProperties.value()).isEqualTo("fern.projection-datasource");
    }

    private Object readField(Object target, String fieldName) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(target);
    }
}
