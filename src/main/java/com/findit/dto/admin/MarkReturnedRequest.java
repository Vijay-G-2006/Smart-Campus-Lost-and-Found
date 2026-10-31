package com.findit.dto.admin;

import jakarta.validation.constraints.Size;

/**
 * Marks an item as returned. When no claim was approved, the administrator has
 * to document the reason (an administrative exception).
 */
public record MarkReturnedRequest(
        @Size(max = 500, message = "Reason must be at most 500 characters")
        String reason
) {
}
