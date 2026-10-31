package com.findit.dto.profile;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Profile update. Only the owner (or an administrator) may call the endpoint.
 */
public record UpdateProfileRequest(
        @NotBlank(message = "Display name is required")
        @Size(min = 2, max = 80, message = "Display name must be between 2 and 80 characters")
        String displayName,

        @Size(max = 120, message = "Contact channel must be at most 120 characters")
        String contactChannel
) {
}
