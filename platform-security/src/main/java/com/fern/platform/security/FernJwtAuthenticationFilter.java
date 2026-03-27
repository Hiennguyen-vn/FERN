package com.fern.platform.security;

import com.fern.platform.common.FernPrincipal;
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

    public FernJwtAuthenticationFilter(FernJwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization != null && authorization.startsWith("Bearer ")) {
            FernJwtClaims claims = jwtService.decode(authorization.substring(7));
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
        }

        filterChain.doFilter(request, response);
    }
}
