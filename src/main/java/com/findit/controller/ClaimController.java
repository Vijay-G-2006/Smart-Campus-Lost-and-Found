package com.findit.controller;

import com.findit.dto.PageResponse;
import com.findit.dto.claim.ClaimResponse;
import com.findit.security.AppUserPrincipal;
import com.findit.service.ClaimService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Claims of the signed in user. A claim can only be read by its claimant and
 * by administrators.
 */
@RestController
@RequestMapping("/api/claims")
public class ClaimController {

    private final ClaimService claimService;

    public ClaimController(ClaimService claimService) {
        this.claimService = claimService;
    }

    @GetMapping("/mine")
    public PageResponse<ClaimResponse> mine(@AuthenticationPrincipal AppUserPrincipal principal,
                                             @RequestParam(required = false) Integer page,
                                             @RequestParam(required = false) Integer size) {
        return claimService.myClaims(principal, page == null ? 0 : page, size == null ? 10 : size);
    }

    @GetMapping("/{id}")
    public ClaimResponse get(@AuthenticationPrincipal AppUserPrincipal principal, @PathVariable Long id) {
        return claimService.getClaimResponse(id, principal);
    }

    @PostMapping("/{id}/withdraw")
    public ClaimResponse withdraw(@AuthenticationPrincipal AppUserPrincipal principal, @PathVariable Long id) {
        return claimService.withdraw(id, principal);
    }
}
