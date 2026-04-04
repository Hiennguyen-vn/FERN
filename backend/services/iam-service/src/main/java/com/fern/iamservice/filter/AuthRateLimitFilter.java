package com.fern.iamservice.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Token-bucket–style rate limiter for auth endpoints.
 *
 * <p>Uses atomic Redis INCR + TTL (sliding window counter) per client IP.
 * Limits:
 * <ul>
 *   <li>{@code POST /auth/login}   — 10 requests / 60 s per IP</li>
 *   <li>{@code POST /auth/refresh} — 30 requests / 60 s per IP</li>
 * </ul>
 */
public class AuthRateLimitFilter extends OncePerRequestFilter {

    private static final String LOGIN_PATH = "/auth/login";
    private static final String REFRESH_PATH = "/auth/refresh";

    /** Max attempts per window for each endpoint. */
    private static final long LOGIN_LIMIT = 10;
    private static final long REFRESH_LIMIT = 30;

    /** Sliding-window duration. */
    private static final Duration WINDOW = Duration.ofMinutes(1);

    /**
     * Atomically increment a counter and set TTL only on the first request.
     * Returns the current count after increment.
     */
    private static final DefaultRedisScript<Long> INCR_SCRIPT = new DefaultRedisScript<>(
            """
            local count = redis.call('INCR', KEYS[1])
            if count == 1 then
                redis.call('PEXPIRE', KEYS[1], ARGV[1])
            end
            return count
            """,
            Long.class
    );

    private final StringRedisTemplate redisTemplate;

    public AuthRateLimitFilter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain
    ) throws ServletException, IOException {
        String path = request.getServletPath();
        String method = request.getMethod();

        if (!"POST".equalsIgnoreCase(method)) {
            chain.doFilter(request, response);
            return;
        }

        long limit;
        String endpointTag;
        if (LOGIN_PATH.equals(path)) {
            limit = LOGIN_LIMIT;
            endpointTag = "login";
        } else if (REFRESH_PATH.equals(path)) {
            limit = REFRESH_LIMIT;
            endpointTag = "refresh";
        } else {
            chain.doFilter(request, response);
            return;
        }

        String clientIp = resolveClientIp(request);
        String redisKey = "fern:iam:rate:" + endpointTag + ":" + clientIp;

        Long count = redisTemplate.execute(
                INCR_SCRIPT,
                java.util.List.of(redisKey),
                String.valueOf(WINDOW.toMillis())
        );

        if (count != null && count > limit) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write(
                    "{\"status\":429,\"error\":\"Too Many Requests\","
                    + "\"message\":\"Rate limit exceeded. Please try again later.\","
                    + "\"path\":\"" + path + "\"}"
            );
            return;
        }

        chain.doFilter(request, response);
    }

    /**
     * Resolves client IP for rate-limiting purposes.
     *
     * <p>Security: We use the <b>rightmost</b> IP in the {@code X-Forwarded-For} chain,
     * which is the IP appended by the last trusted reverse proxy (load balancer).
     * The leftmost IP is client-controlled and trivially spoofable — an attacker can
     * send {@code X-Forwarded-For: fake-ip} to bypass per-IP rate limits.
     *
     * <p>When no proxy headers are present (direct connection), falls back to
     * {@code request.getRemoteAddr()}.
     */
    private static String resolveClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            // Take the rightmost (proxy-appended) IP — this is the only trustworthy entry
            // because the reverse proxy always appends the real client IP at the end.
            String[] parts = xff.split(",");
            return parts[parts.length - 1].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        return request.getRemoteAddr();
    }
}
