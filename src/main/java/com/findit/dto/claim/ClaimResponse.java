package com.findit.dto.claim;

import com.findit.domain.ClaimStatus;

import java.time.Instant;

/**
 * A claim. The confidential fields are only included for the claimant and for
 * administrators - for everybody else they are {@code null}.
 */
public record ClaimResponse(
        Long id,
        Long itemId,
        String itemTitle,
        Long claimantId,
        String claimantDisplayName,
        String explanation,
        String verificationAnswers,
        ClaimStatus status,
        Long reviewedById,
        String reviewedByDisplayName,
        Instant reviewedAt,
        String reviewReason,
        Instant createdAt,
        Instant updatedAt,
        boolean canWithdraw
) {
}
