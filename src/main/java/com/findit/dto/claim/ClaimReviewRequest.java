package com.findit.dto.claim;

import jakarta.validation.constraints.Size;

/**
 * Administrator decision on a claim. A reason is mandatory when rejecting.
 */
public record ClaimReviewRequest(
        @Size(max = 1000, message = "Reason must be at most 1000 characters")
        String reason
) {
}
