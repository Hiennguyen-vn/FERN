package com.fern.platform.security;

import com.fern.platform.common.FernPrincipal;
import com.fern.platform.common.UnauthorizedException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.stream.Collectors;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public class FernJwtAuthenticationFilter extends OncePerRequestFilter {
    private final FernJwtService jwtService;
    private final FernTokenAcceptanceValidator tokenAcceptanceValidator;

    public FernJwtAuthenticationFilter(FernJwtService jwtService) {
        this(jwtService, FernTokenAcceptanceValidator.noop());
    }

    public FernJwtAuthenticationFilter(FernJwtService jwtService, FernTokenAcceptanceValidator tokenAcceptanceValidator) {
        this.jwtService = jwtService;
        this.tokenAcceptanceValidator = tokenAcceptanceValidator;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization != null && authorization.startsWith("Bearer ")) {
            try {
                FernJwtClaims claims = jwtService.decode(authorization.substring(7));
                tokenAcceptanceValidator.validate(claims);
                FernPrincipal principal = claims.toPrincipal();
                var authorities = principal.permissions().stream()
                        .map(SimpleGrantedAuthority::new)
                        .collect(Collectors.toSet());
                principal.roles().stream()
                        .map(role -> new SimpleGrantedAuthority("ROLE_" + role.toUpperCase()))
                        .forEach(authorities::add);
                SecurityContextHolder.getContext().setAuthentication(
                        new UsernamePasswordAuthenticationToken(principal, authorization, authorities)
                );
            } catch (RuntimeException exception) {
                SecurityContextHolder.clearContext();
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, unauthorizedMessage(exception));
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    private String unauthorizedMessage(RuntimeException exception) {
        if (exception instanceof UnauthorizedException) {
            return exception.getMessage();
        }
        return "Invalid bearer token";
    }
}
