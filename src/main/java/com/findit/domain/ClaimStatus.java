package com.findit.domain;

/**
 * Lifecycle of an ownership claim.
 */
public enum ClaimStatus {
    /** Waiting for administrator review. */
    PENDING,
    /** Approved by an administrator - the item can be marked returned. */
    APPROVED,
    /** Rejected by an administrator. */
    REJECTED,
    /** Withdrawn by the claimant. */
    WITHDRAWN
}
