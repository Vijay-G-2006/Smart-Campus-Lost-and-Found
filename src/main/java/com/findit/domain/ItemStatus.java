package com.findit.domain;

/**
 * Lifecycle of a lost/found report. The allowed transitions are enforced by
 * {@code com.findit.service.ItemStatusPolicy} and never by the client.
 */
public enum ItemStatus {
    /** Open for matching, searching and claiming. */
    OPEN,
    /** At least one claim is waiting for administrator review. */
    UNDER_REVIEW,
    /** A claim was approved and the item is being handed over. */
    CLAIMED,
    /** The item was handed back to its owner. */
    RETURNED,
    /** Closed by the owner or an administrator. */
    CLOSED,
    /** Rejected / moderated by an administrator. */
    REJECTED
}
