package com.findit.dto.item;

import com.findit.domain.ItemType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Payload for creating a lost or found report.
 */
public record ItemCreateRequest(
        @NotNull(message = "Type is required") ItemType type,

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
