package com.fern.catalogservice.config;

import com.fern.platform.observability.ServletCorrelationIdFilter;
import com.fern.platform.security.FernJwtAuthenticationFilter;
import com.fern.platform.security.FernJwtService;
import com.fern.platform.security.RedisFernTokenAcceptanceValidator;
import org.springframework.beans.factory.ObjectProvider;
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
            ObjectProvider<StringRedisTemplate> redisTemplateProvider
    ) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(registry -> registry
                        .requestMatchers("/actuator/health").permitAll()
                        .requestMatchers(HttpMethod.GET, "/actuator/info").permitAll()
                        .anyRequest().authenticated()
                )
                .addFilterBefore(new ServletCorrelationIdFilter(), UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(
                        new FernJwtAuthenticationFilter(
                                jwtService,
                                redisTemplateProvider.getIfAvailable() == null
                                        ? com.fern.platform.security.FernTokenAcceptanceValidator.noop()
                                        : new RedisFernTokenAcceptanceValidator(redisTemplateProvider.getIfAvailable())
                        ),
                        UsernamePasswordAuthenticationFilter.class
                )
                .build();
    }
}
