package com.fern.financeservice.config;

import com.fern.platform.observability.ServletCorrelationIdFilter;
import com.fern.platform.security.FernJwtAuthenticationFilter;
import com.fern.platform.security.FernJwtProperties;
import com.fern.platform.security.FernJwtService;
import com.fern.platform.security.FernTokenAcceptanceValidator;
import com.fern.platform.security.RedisFernTokenAcceptanceValidator;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class SecurityConfig {
    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            FernJwtService jwtService,
            ObjectProvider<StringRedisTemplate> redisTemplateProvider,
            FernJwtProperties jwtProperties,
            @Value("${spring.application.name}") String serviceName
    ) throws Exception {
        StringRedisTemplate redisTemplate = redisTemplateProvider.getIfAvailable();
        FernTokenAcceptanceValidator validator = redisTemplate == null
                ? FernTokenAcceptanceValidator.noop()
                : new RedisFernTokenAcceptanceValidator(redisTemplate, serviceName, jwtProperties.getUserTokenIssuer());
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(registry -> registry
                        .requestMatchers("/actuator/health").permitAll()
                        .requestMatchers(HttpMethod.GET, "/actuator/info").permitAll()
                        .requestMatchers(HttpMethod.GET, "/actuator/prometheus").permitAll()
                        .anyRequest().authenticated()
                )
                .addFilterBefore(new ServletCorrelationIdFilter(), UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(new FernJwtAuthenticationFilter(jwtService, validator), UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
