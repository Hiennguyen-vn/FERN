package com.fern.financeservice.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.fern.platform.web.FernDownstreamClientFactory;
import com.fern.platform.web.FernDownstreamClientProperties;
import com.fern.platform.web.FernDownstreamClientSpec;
import java.lang.reflect.Field;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

class FinanceBeansTest {
    private final FinanceBeans financeBeans = new FinanceBeans();

    @Test
    void shouldConfigureRestClientTimeouts() throws Exception {
        FernDownstreamClientProperties properties = new FernDownstreamClientProperties();
        properties.setBaseUrl("http://localhost:8085");
        properties.setConnectTimeout(java.time.Duration.ofSeconds(5));
        properties.setReadTimeout(java.time.Duration.ofSeconds(10));
        RestClient restClient = financeBeans.restClient(
                new FernDownstreamClientSpec("finance-service", "hr-service", "hr", properties),
                new FernDownstreamClientFactory(new SimpleMeterRegistry())
        );

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
