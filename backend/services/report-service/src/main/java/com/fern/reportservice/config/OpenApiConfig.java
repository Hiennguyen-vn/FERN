package com.fern.reportservice.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI / Swagger UI configuration for Report Service.
 * UI available at /swagger-ui.html when the service is running.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI openApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Report Service")
                        .version("1.0")
                        .description("Revenue, inventory, and payroll report generation and export management."));
    }
}
