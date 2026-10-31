package com.findit.dto.admin;

/**
 * Aggregated counters shown on the administrator dashboard.
 */
public record DashboardCounts(
        long openLostReports,
        long openFoundReports,
        long pendingClaims,
        long approvedClaims,
        long returnedItems,
        long closedReports,
        long totalReports,
        long totalUsers
) {
}
