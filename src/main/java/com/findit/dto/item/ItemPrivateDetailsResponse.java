package com.findit.dto.item;

/**
 * Private part of a report. Only the owner of the report and administrators
 * are allowed to read it.
 */
public record ItemPrivateDetailsResponse(
        Long itemId,
        String privateVerificationDetail,
        String contactChannel,
        String createdByEmail
) {
}
