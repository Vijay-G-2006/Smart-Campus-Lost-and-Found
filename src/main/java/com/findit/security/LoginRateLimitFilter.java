package com.findit.security;

import tools.jackson.databind.ObjectMapper;
import com.findit.config.AppProperties;
import com.findit.dto.common.ApiError;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;

/**
 * Basic abuse protection for the sign-in endpoint: too many attempts from the
 * same client (IP + email) inside the configured window are rejected with 429.
 */
@Component
public class LoginRateLimitFilter extends OncePerRequestFilter {

    private final RateLimiter rateLimiter;
    private final AppProperties appProperties;
    private final ObjectMapper objectMapper;

    public LoginRateLimitFilter(RateLimiter rateLimiter, AppProperties appProperties, ObjectMapper objectMapper) {
        this.rateLimiter = rateLimiter;
        this.appProperties = appProperties;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        boolean allowed = rateLimiter.tryAcquire(
                RateLimitKeys.forLogin(request),
                appProperties.getSecurity().getMaxLoginAttempts(),
                appProperties.getSecurity().getRateLimitWindowSeconds());

        if (!allowed) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            ApiError body = new ApiError(Instant.now(), HttpStatus.TOO_MANY_REQUESTS.value(),
                    HttpStatus.TOO_MANY_REQUESTS.getReasonPhrase(),
                    "Too many sign-in attempts. Please try again later.",
                    request.getRequestURI(), Map.of());
            objectMapper.writeValue(response.getOutputStream(), body);
            return;
        }
        filterChain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !("POST".equalsIgnoreCase(request.getMethod())
                && "/api/auth/login".equals(request.getServletPath()));
    }
}
