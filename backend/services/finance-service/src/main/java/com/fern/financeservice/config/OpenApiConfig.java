package com.fern.financeservice.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI / Swagger UI configuration for Finance Service.
 * UI available at /swagger-ui.html when the service is running.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI openApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Finance Service")
                        .version("1.0")
                        .description("Payroll processing, finance configuration, supplier invoices, and payment workflows."));
    }
}
