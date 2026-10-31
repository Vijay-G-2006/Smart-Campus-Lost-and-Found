package com.findit.service;

import com.findit.domain.Claim;
import com.findit.domain.ClaimStatus;
import com.findit.domain.Item;
import com.findit.domain.ItemStatus;
import com.findit.dto.PageResponse;
import com.findit.dto.claim.ClaimCreateRequest;
import com.findit.dto.claim.ClaimResponse;
import com.findit.exception.BadRequestException;
import com.findit.exception.ConflictException;
import com.findit.exception.ForbiddenException;
import com.findit.exception.NotFoundException;
import com.findit.repository.ClaimRepository;
import com.findit.repository.ItemRepository;
import com.findit.security.AppUserPrincipal;
import com.findit.security.RateLimitKeys;
import com.findit.security.RateLimiter;
import com.findit.config.AppProperties;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Claim workflow. Claim texts and verification answers are confidential and are
 * only ever returned to the claimant and to administrators.
 */
@Service
public class ClaimService {

    private static final List<ClaimStatus> ACTIVE_CLAIM_STATUSES = List.of(ClaimStatus.PENDING, ClaimStatus.APPROVED);

    private final ClaimRepository claimRepository;
    private final ItemRepository itemRepository;
    private final ItemStatusPolicy policy;
    private final UserService userService;
    private final AuditService auditService;
    private final RateLimiter rateLimiter;
    private final AppProperties appProperties;

    public ClaimService(ClaimRepository claimRepository, ItemRepository itemRepository, ItemStatusPolicy policy,
                        UserService userService, AuditService auditService, RateLimiter rateLimiter,
                        AppProperties appProperties) {
        this.claimRepository = claimRepository;
        this.itemRepository = itemRepository;
        this.policy = policy;
        this.userService = userService;
        this.auditService = auditService;
        this.rateLimiter = rateLimiter;
        this.appProperties = appProperties;
    }

    // ----------------------------------------------------------------- claims

    @Transactional
    public ClaimResponse submit(Long itemId, ClaimCreateRequest request, AppUserPrincipal viewer) {
        Item item = itemRepository.findById(itemId)
                .orElseThrow(() -> new NotFoundException("Report not found"));

        if (!policy.acceptsNewClaims(item)) {
            throw new ConflictException("This report does not accept claims any more (status "
                    + item.getStatus() + ")");
        }
        if (policy.isOwner(item, viewer)) {
            throw new ForbiddenException("You cannot claim your own report");
        }
        if (claimRepository.existsByItemIdAndClaimantIdAndStatusIn(item.getId(), viewer.getId(), ACTIVE_CLAIM_STATUSES)) {
            throw new ConflictException("You already have an active claim for this report");
        }
        if (!rateLimiter.tryAcquire(RateLimitKeys.forClaims(viewer.getId()),
                appProperties.getSecurity().getMaxClaimsPerHour(), 3600)) {
            throw new ConflictException("Too many claims were submitted in the last hour - please try again later");
        }

        Claim claim = new Claim(item, userService.getById(viewer.getId()),
                request.explanation().trim(), normaliseOptional(request.verificationAnswers()));
        Claim saved = claimRepository.save(claim);

        if (item.getStatus() == ItemStatus.OPEN) {
            item.setStatus(ItemStatus.UNDER_REVIEW);
            itemRepository.save(item);
        }
        auditService.record(viewer.getId(), "CLAIM_SUBMITTED", "CLAIM", saved.getId(),
                Map.of("itemId", String.valueOf(item.getId())));
        return toResponse(saved, viewer);
    }

    @Transactional(readOnly = true)
    public Claim getClaim(Long id) {
        return claimRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Claim not found"));
    }

    /**
     * A claim may only be read by its claimant and by administrators.
     */
    @Transactional(readOnly = true)
    public ClaimResponse getClaimResponse(Long id, AppUserPrincipal viewer) {
        Claim claim = getClaim(id);
        boolean allowed = viewer != null && (viewer.isAdmin() || claim.getClaimant().getId().equals(viewer.getId()));
        if (!allowed) {
            throw new ForbiddenException("You may only read your own claims");
        }
        return toResponse(claim, viewer);
    }

    @Transactional(readOnly = true)
    public PageResponse<ClaimResponse> myClaims(AppUserPrincipal viewer, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 50),
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
        Specification<Claim> specification = (root, query, cb) ->
                cb.equal(root.get("claimant").get("id"), viewer.getId());
        Page<Claim> result = claimRepository.findAll(specification, pageable);
        return PageResponse.of(result, claim -> toResponse(claim, viewer));
    }

    @Transactional
    public ClaimResponse withdraw(Long claimId, AppUserPrincipal viewer) {
        Claim claim = claimRepository.findById(claimId)
                .orElseThrow(() -> new NotFoundException("Claim not found"));
        if (!claim.getClaimant().getId().equals(viewer.getId())) {
            throw new ForbiddenException("You can only withdraw your own claim");
        }
        if (claim.getStatus() != ClaimStatus.PENDING) {
            throw new ConflictException("Only a pending claim can be withdrawn");
        }
        claim.setStatus(ClaimStatus.WITHDRAWN);
        claim.setWithdrawnAt(Instant.now());
        Claim saved = claimRepository.save(claim);
        releaseItemIfNoPendingClaims(saved.getItem());
        auditService.record(viewer.getId(), "CLAIM_WITHDRAWN", "CLAIM", saved.getId(), null);
        return toResponse(saved, viewer);
    }

    // ------------------------------------------------------------ admin queue

    @Transactional(readOnly = true)
    public PageResponse<ClaimResponse> listAll(ClaimStatus status, String keyword, int page, int size,
                                                AppUserPrincipal admin) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 50),
                Sort.by(Sort.Order.asc("createdAt"), Sort.Order.asc("id")));
        Specification<Claim> specification = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (keyword != null && !keyword.isBlank()) {
                String pattern = "%" + keyword.trim().toLowerCase() + "%";
                predicates.add(cb.like(cb.lower(root.get("item").get("title")), pattern));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        Page<Claim> result = claimRepository.findAll(specification, pageable);
        return PageResponse.of(result, claim -> toResponse(claim, admin));
    }

    @Transactional
    public ClaimResponse approve(Long claimId, String reason, AppUserPrincipal admin) {
        Claim claim = claimRepository.findById(claimId)
                .orElseThrow(() -> new NotFoundException("Claim not found"));
        if (claim.getStatus() != ClaimStatus.PENDING) {
            throw new ConflictException("Only a pending claim can be approved");
        }
        claim.setStatus(ClaimStatus.APPROVED);
        claim.setReviewedBy(userService.getById(admin.getId()));
        claim.setReviewedAt(Instant.now());
        claim.setReviewReason(normaliseOptional(reason));
        Claim saved = claimRepository.save(claim);

        Item item = saved.getItem();
        if (item.getStatus() != ItemStatus.CLAIMED && policy.isTransitionAllowed(item.getStatus(), ItemStatus.CLAIMED)) {
            item.setStatus(ItemStatus.CLAIMED);
            itemRepository.save(item);
        }
        // The item now belongs to the approved claimant: competing claims are closed.
        for (Claim other : claimRepository.findAll((root, query, cb) -> cb.and(
                cb.equal(root.get("item").get("id"), item.getId()),
                cb.equal(root.get("status"), ClaimStatus.PENDING),
                cb.notEqual(root.get("id"), saved.getId())))) {
            other.setStatus(ClaimStatus.REJECTED);
            other.setReviewedBy(userService.getById(admin.getId()));
            other.setReviewedAt(Instant.now());
            other.setReviewReason("The item was already approved for another claimant");
            claimRepository.save(other);
        }
        auditService.record(admin.getId(), "CLAIM_APPROVED", "CLAIM", saved.getId(),
                Map.of("itemId", String.valueOf(item.getId())));
        return toResponse(saved, admin);
    }

    @Transactional
    public ClaimResponse reject(Long claimId, String reason, AppUserPrincipal admin) {
        Claim claim = claimRepository.findById(claimId)
                .orElseThrow(() -> new NotFoundException("Claim not found"));
        if (claim.getStatus() != ClaimStatus.PENDING) {
            throw new ConflictException("Only a pending claim can be rejected");
        }
        if (reason == null || reason.isBlank()) {
            throw new BadRequestException("A reason is required when rejecting a claim");
        }
        claim.setStatus(ClaimStatus.REJECTED);
        claim.setReviewedBy(userService.getById(admin.getId()));
        claim.setReviewedAt(Instant.now());
        claim.setReviewReason(reason.trim());
        Claim saved = claimRepository.save(claim);

        releaseItemIfNoPendingClaims(saved.getItem());
        auditService.record(admin.getId(), "CLAIM_REJECTED", "CLAIM", saved.getId(),
                Map.of("itemId", String.valueOf(saved.getItem().getId())));
        return toResponse(saved, admin);
    }

    // --------------------------------------------------------------- helpers

    /**
     * Sets the item back to {@code OPEN} when no pending claim is left.
     */
    private void releaseItemIfNoPendingClaims(Item item) {
        boolean pendingLeft = claimRepository.existsByItemIdAndStatus(item.getId(), ClaimStatus.PENDING);
        if (!pendingLeft && item.getStatus() == ItemStatus.UNDER_REVIEW) {
            item.setStatus(ItemStatus.OPEN);
            itemRepository.save(item);
        }
    }

    /**
     * Maps a claim. Confidential fields are only filled in for the claimant
     * and for administrators.
     */
    @Transactional(readOnly = true)
    public ClaimResponse toResponse(Claim claim, AppUserPrincipal viewer) {
        boolean privileged = viewer != null
                && (viewer.isAdmin() || claim.getClaimant().getId().equals(viewer.getId()));
        String explanation = privileged ? claim.getExplanation() : null;
        String verificationAnswers = privileged ? claim.getVerificationAnswers() : null;
        String reviewReason = privileged ? claim.getReviewReason() : null;
        String reviewerName = claim.getReviewedBy() == null ? null : claim.getReviewedBy().getDisplayName();

        return new ClaimResponse(
                claim.getId(),
                claim.getItem().getId(),
                claim.getItem().getTitle(),
                claim.getClaimant().getId(),
                claim.getClaimant().getDisplayName(),
                explanation,
                verificationAnswers,
                claim.getStatus(),
                claim.getReviewedBy() == null ? null : claim.getReviewedBy().getId(),
                reviewerName,
                claim.getReviewedAt(),
                reviewReason,
                claim.getCreatedAt(),
                claim.getUpdatedAt(),
                claim.getStatus() == ClaimStatus.PENDING
                        && viewer != null
                        && claim.getClaimant().getId().equals(viewer.getId()));
    }

    private String normaliseOptional(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() > 1000 ? trimmed.substring(0, 1000) : trimmed;
    }
}
