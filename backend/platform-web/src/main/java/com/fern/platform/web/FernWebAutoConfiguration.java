package com.fern.platform.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(FernGlobalExceptionHandler.class)
public class FernWebAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(FernGlobalExceptionHandler.class)
    public FernGlobalExceptionHandler fernGlobalExceptionHandler(
            @Value("${spring.application.name:application}") String applicationName,
            ObjectProvider<UnhandledExceptionListener> unhandledListeners
    ) {
        List<UnhandledExceptionListener> list = unhandledListeners.stream().toList();
        return new FernGlobalExceptionHandler(applicationName, list);
    }

    @Bean
    @ConditionalOnMissingBean
    public FernDownstreamErrorMapper fernDownstreamErrorMapper(ObjectMapper objectMapper) {
        return new FernDownstreamErrorMapper(objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public FernDownstreamClientFactory fernDownstreamClientFactory(MeterRegistry meterRegistry) {
        return new FernDownstreamClientFactory(meterRegistry);
    }
}
