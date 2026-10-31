package com.findit.dto.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Administrative moderation of a report.
 */
public record ModerateItemRequest(
        @NotBlank(message = "Action is required") ModerationAction action,

        @Size(max = 500, message = "Reason must be at most 500 characters")
        String reason
) {

    public enum ModerationAction {
        /** Close the report without rejecting it. */
        CLOSE,
        /** Reject / hide the report. */
        REJECT,
        /** Re-open a moderated report. */
        REOPEN
    }
}
