package com.fern.auditservice.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI / Swagger UI configuration for Audit Service.
 * UI available at /swagger-ui.html when the service is running.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI openApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Audit Service")
                        .version("1.0")
                        .description("Records and retrieves immutable audit events for all FERN domain operations."));
    }
}
