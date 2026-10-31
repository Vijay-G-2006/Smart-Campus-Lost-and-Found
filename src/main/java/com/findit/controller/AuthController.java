package com.findit.controller;

import com.findit.dto.auth.RegisterRequest;
import com.findit.dto.auth.UserResponse;
import com.findit.security.AppUserPrincipal;
import com.findit.service.AuthService;
import com.findit.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Registration, session information and the CSRF token.
 *
 * <p>Sign-in and sign-out are handled by Spring Security
 * ({@code POST /api/auth/login}, {@code POST /api/auth/logout}) so that
 * session fixation protection, CSRF rotation and rate limiting are applied
 * consistently.</p>
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final UserService userService;

    public AuthController(AuthService authService, UserService userService) {
        this.authService = authService;
        this.userService = userService;
    }

    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    /**
     * Returns the CSRF token that has to be sent in the {@code X-XSRF-TOKEN}
     * header for every state changing request.
     */
    @GetMapping("/csrf")
    public Map<String, String> csrf(CsrfToken csrfToken) {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("csrfToken", csrfToken.getToken());
        body.put("headerName", csrfToken.getHeaderName());
        body.put("parameterName", csrfToken.getParameterName());
        return body;
    }

    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal AppUserPrincipal principal) {
        return userService.getProfile(principal.getId());
    }
}
