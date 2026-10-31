package com.findit.dto.claim;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Private claim payload. Both fields stay confidential and are only visible to
 * the claimant and to administrators.
 */
public record ClaimCreateRequest(
        @NotBlank(message = "Explanation is required")
        @Size(min = 20, max = 2000, message = "Explanation must be between 20 and 2000 characters")
        String explanation,

        @Size(max = 1000, message = "Verification answers must be at most 1000 characters")
        String verificationAnswers
) {
}
