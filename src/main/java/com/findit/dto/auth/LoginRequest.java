package com.findit.dto.auth;

import jakarta.validation.constraints.NotBlank;

/**
 * Login payload used by the JSON login endpoint.
 */
public record LoginRequest(
        @NotBlank(message = "Email is required") String email,
        @NotBlank(message = "Password is required") String password
) {
}
