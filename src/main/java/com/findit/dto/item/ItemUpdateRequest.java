package com.findit.dto.item;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Payload for editing an own, still open report. The type cannot be changed
 * after creation because it would invalidate existing matches and claims.
 */
public record ItemUpdateRequest(
        @NotBlank(message = "Title is required")
        @Size(min = 3, max = 100, message = "Title must be between 3 and 100 characters")
        String title,

        @NotNull(message = "Category is required") Long categoryId,

        @NotNull(message = "Location is required") Long locationId,

        @NotBlank(message = "Description is required")
        @Size(min = 10, max = 2000, message = "Description must be between 10 and 2000 characters")
        String description,

        @NotNull(message = "Date is required") LocalDate eventDate,

        @Size(max = 1000, message = "Verification detail must be at most 1000 characters")
        String privateVerificationDetail
) {
}
