package com.findit.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Registration payload. The role is never accepted from the client.
 */
public record RegisterRequest(
        @NotBlank(message = "Email is required")
        @Email(message = "Enter a valid email address")
        @Size(max = 180, message = "Email must be at most 180 characters")
        String email,

        @NotBlank(message = "Display name is required")
        @Size(min = 2, max = 80, message = "Display name must be between 2 and 80 characters")
        String displayName,

        @NotBlank(message = "Password is required")
        @Size(min = 10, max = 72, message = "Password must be between 10 and 72 characters")
        @Pattern(regexp = ".*[A-Za-z].*", message = "Password must contain a letter")
        @Pattern(regexp = ".*\\d.*", message = "Password must contain a digit")
        String password
) {
}
