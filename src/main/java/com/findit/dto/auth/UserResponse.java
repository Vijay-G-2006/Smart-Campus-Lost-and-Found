package com.findit.dto.auth;

import com.findit.domain.Role;

import java.time.Instant;

/**
 * Safe representation of the signed in user. It never contains the password hash.
 */
public record UserResponse(
        Long id,
        String email,
        String displayName,
        Role role,
        boolean active,
        String contactChannel,
        Instant createdAt
) {
}
