package com.findit.dto.match;

import com.findit.domain.ItemType;

import java.time.LocalDate;

/**
 * A scored, explainable match suggestion.
 *
 * <p>A score is a hint only. It is never proof of ownership - every claim has
 * to be verified manually by an administrator.</p>
 */
public record MatchResponse(
        Long itemId,
        ItemType itemType,
        String title,
        String categoryName,
        String locationName,
        LocalDate eventDate,
        int score,
        String band,
        MatchBreakdown breakdown,
        String disclaimer
) {

    public static final String DISCLAIMER =
            "Suggested match only - a score is not proof of ownership. "
                    + "An administrator must verify every claim manually.";

    /**
     * Explainable, deterministic score breakdown (total is capped at 100).
     */
    public record MatchBreakdown(
            int categoryPoints,
            int descriptionPoints,
            int locationPoints,
            int datePoints,
            int total,
            String explanation
    ) {
    }
}
