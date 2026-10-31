package com.findit.dto.item;

import com.findit.domain.ItemStatus;
import com.findit.domain.ItemType;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Public view of a lost/found report.
 *
 * <p>Deliberately contains no private verification detail, no claim content and
 * no contact details of the reporter. Private information is only exposed by
 * the dedicated owner/administrator endpoints.</p>
 */
public record ItemResponse(
        Long id,
        ItemType type,
        String title,
        String description,
        Long categoryId,
        String categoryName,
        Long locationId,
        String locationName,
        LocalDate eventDate,
        ItemStatus status,
        Long createdById,
        String createdByDisplayName,
        Instant createdAt,
        Instant updatedAt,
        boolean hasImage,
        String imageUrl,
        Instant returnedAt,
        Instant closedAt,
        boolean owner,
        boolean canEdit,
        boolean canClose,
        boolean canClaim
) {
}
