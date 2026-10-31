package com.findit.controller;

import com.findit.domain.ClaimStatus;
import com.findit.dto.PageResponse;
import com.findit.dto.admin.AdminDashboardResponse;
import com.findit.dto.admin.MarkReturnedRequest;
import com.findit.dto.admin.ModerateItemRequest;
import com.findit.dto.audit.AuditLogResponse;
import com.findit.dto.claim.ClaimResponse;
import com.findit.dto.claim.ClaimReviewRequest;
import com.findit.dto.item.ItemResponse;
import com.findit.security.AppUserPrincipal;
import com.findit.service.AdminService;
import com.findit.service.ClaimService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Administrator endpoints. Access is additionally enforced by the route
 * configuration ({@code /api/admin/**} requires the ADMIN role) and by the
 * services themselves.
 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminService adminService;
    private final ClaimService claimService;

    public AdminController(AdminService adminService, ClaimService claimService) {
        this.adminService = adminService;
        this.claimService = claimService;
    }

    @GetMapping("/dashboard")
    public AdminDashboardResponse dashboard(@AuthenticationPrincipal AppUserPrincipal principal) {
        return adminService.dashboard(principal);
    }

    @GetMapping("/claims")
    public PageResponse<ClaimResponse> claims(@AuthenticationPrincipal AppUserPrincipal principal,
                                               @RequestParam(required = false) ClaimStatus status,
                                               @RequestParam(required = false) String q,
                                               @RequestParam(required = false) Integer page,
                                               @RequestParam(required = false) Integer size) {
        return claimService.listAll(status, q, page == null ? 0 : page, size == null ? 10 : size, principal);
    }

    @PostMapping("/claims/{id}/approve")
    public ClaimResponse approve(@AuthenticationPrincipal AppUserPrincipal principal, @PathVariable Long id,
                                  @Valid @RequestBody(required = false) ClaimReviewRequest request) {
        String reason = request == null ? null : request.reason();
        return claimService.approve(id, reason, principal);
    }

    @PostMapping("/claims/{id}/reject")
    public ClaimResponse reject(@AuthenticationPrincipal AppUserPrincipal principal, @PathVariable Long id,
                                @Valid @RequestBody(required = false) ClaimReviewRequest request) {
        String reason = request == null ? null : request.reason();
        return claimService.reject(id, reason, principal);
    }

    @PostMapping("/items/{id}/mark-returned")
    public ItemResponse markReturned(@AuthenticationPrincipal AppUserPrincipal principal, @PathVariable Long id,
                                     @Valid @RequestBody(required = false) MarkReturnedRequest request) {
        return adminService.markReturned(id, request == null ? new MarkReturnedRequest(null) : request, principal);
    }

    @PostMapping("/items/{id}/moderate")
    public ItemResponse moderate(@AuthenticationPrincipal AppUserPrincipal principal, @PathVariable Long id,
                                 @Valid @RequestBody ModerateItemRequest request) {
        return adminService.moderate(id, request, principal);
    }

    @GetMapping("/audit-logs")
    public PageResponse<AuditLogResponse> auditLogs(@RequestParam(required = false) Integer page,
                                                     @RequestParam(required = false) Integer size) {
        return adminService.auditLogs(page == null ? 0 : page, size == null ? 25 : size);
    }
}
