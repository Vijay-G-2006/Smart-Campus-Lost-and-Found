package com.findit.dto.admin;

import com.findit.dto.claim.ClaimResponse;
import com.findit.dto.item.ItemResponse;

import java.util.List;

/**
 * Administrator dashboard payload: counters plus the two work queues.
 */
public record AdminDashboardResponse(
        DashboardCounts counts,
        List<ClaimResponse> pendingClaims,
        List<ItemResponse> recentlyUpdatedReports
) {
}
