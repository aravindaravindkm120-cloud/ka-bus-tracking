package com.kabus.tracking.config;

import com.kabus.tracking.support.RateLimitService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Coarse IP-based rate limiting used to protect login, public endpoints and
 * GPS uploads from abuse.
 *
 * <p>Authoritative per-session GPS throttling (min interval between uploads)
 * is additionally enforced in GpsService. WebSocket connection-rate limiting
 * is described in docs/SCALABILITY.md; the STOMP handshake is a plain HTTP
 * upgrade that benefits from the public-endpoint limits here too.</p>
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimitService rateLimitService;
    private final AppProperties props;

    public RateLimitFilter(RateLimitService rateLimitService, AppProperties props) {
        this.rateLimitService = rateLimitService;
        this.props = props;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/ws") || path.equals("/") || request.getMethod().equals("OPTIONS");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        String path = request.getRequestURI();
        String ip = clientIp(request);

        Limit limit = null;
        String key = null;
        if (path.startsWith("/api/auth/login") || path.startsWith("/api/auth/admin/login")) {
            key = "login:" + ip;
            limit = new Limit("login", props.getRateLimit().getLoginPerMinute(), props.getRateLimit().getLoginPerMinute());
        } else if (path.startsWith("/api/crew/gps/")) {
            key = "gps:" + ip;
            limit = new Limit("gps", props.getRateLimit().getGpsPerMinute(), props.getRateLimit().getGpsPerMinute());
        } else if (path.startsWith("/api/public/")) {
            key = "public:" + ip;
            limit = new Limit("public", props.getRateLimit().getPublicRequestsPerMinute(), props.getRateLimit().getPublicRequestsPerMinute());
        }

        if (limit == null) {
            chain.doFilter(request, response);
            return;
        }

        if (!rateLimitService.allow(key, limit.capacity, limit.perMinute)) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.setHeader("Retry-After", "5");
            response.getWriter().write("{\"status\":429,\"error\":\"Too Many Requests\","
                    + "\"message\":\"Rate limit exceeded for " + limit.name + ".\"}");
            return;
        }

        chain.doFilter(request, response);
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private record Limit(String name, int capacity, int perMinute) {
    }
}