package com.findit.security;

import tools.jackson.databind.ObjectMapper;
import com.findit.dto.auth.UserResponse;
import com.findit.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Returns the signed in user (and a fresh CSRF token) after a successful login.
 */
@Component
public class RestAuthenticationSuccessHandler implements AuthenticationSuccessHandler {

    private final ObjectMapper objectMapper;
    private final UserService userService;
    private final RateLimiter rateLimiter;

    public RestAuthenticationSuccessHandler(ObjectMapper objectMapper, UserService userService, RateLimiter rateLimiter) {
        this.objectMapper = objectMapper;
        this.userService = userService;
        this.rateLimiter = rateLimiter;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        AppUserPrincipal principal = (AppUserPrincipal) authentication.getPrincipal();
        UserResponse user = userService.toResponse(userService.getById(principal.getId()));
        rateLimiter.reset(RateLimitKeys.forLogin(request));

        CsrfToken csrfToken = (CsrfToken) request.getAttribute(CsrfToken.class.getName());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("user", user);
        body.put("csrfToken", csrfToken != null ? csrfToken.getToken() : null);

        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
