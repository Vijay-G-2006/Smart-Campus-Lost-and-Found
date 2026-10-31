package com.findit.security;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Builds the rate limit keys. Kept in one place so the filter and the
 * handlers always use exactly the same key.
 */
public final class RateLimitKeys {

    private RateLimitKeys() {
    }

    public static String forLogin(HttpServletRequest request) {
        String email = request.getParameter("email");
        String ip = request.getRemoteAddr();
        return "login:" + ip + "|" + (email == null ? "" : email.toLowerCase());
    }

    public static String forClaims(Long userId) {
        return "claims:" + userId;
    }
}
