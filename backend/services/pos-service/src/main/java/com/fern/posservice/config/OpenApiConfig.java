package com.fern.posservice.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI / Swagger UI configuration for POS Service.
 * UI available at /swagger-ui.html when the service is running.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI openApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("POS Service")
                        .version("1.0")
                        .description("Point-of-sale sessions, orders, and real-time transaction management."));
    }
}
