package com.findit.service;

import com.findit.domain.AuditLog;
import com.findit.domain.Claim;
import com.findit.domain.ClaimStatus;
import com.findit.domain.Item;
import com.findit.domain.ItemStatus;
import com.findit.domain.ItemType;
import com.findit.dto.PageResponse;
import com.findit.dto.admin.AdminDashboardResponse;
import com.findit.dto.admin.DashboardCounts;
import com.findit.dto.admin.MarkReturnedRequest;
import com.findit.dto.admin.ModerateItemRequest;
import com.findit.dto.audit.AuditLogResponse;
import com.findit.dto.item.ItemResponse;
import com.findit.exception.BadRequestException;
import com.findit.exception.ConflictException;
import com.findit.exception.ForbiddenException;
import com.findit.exception.NotFoundException;
import com.findit.repository.AuditLogRepository;
import com.findit.repository.ClaimRepository;
import com.findit.repository.ItemRepository;
import com.findit.repository.UserRepository;
import com.findit.security.AppUserPrincipal;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Administrator tasks: dashboard, claim queue, moderation and handover.
 */
@Service
public class AdminService {

    private static final int DASHBOARD_LIST_SIZE = 10;

    private final ItemRepository itemRepository;
    private final ClaimRepository claimRepository;
    private final UserRepository userRepository;
    private final AuditLogRepository auditLogRepository;
    private final ItemStatusPolicy policy;
    private final ItemService itemService;
    private final ClaimService claimService;
    private final UserService userService;
    private final AuditService auditService;

    public AdminService(ItemRepository itemRepository, ClaimRepository claimRepository,
                        UserRepository userRepository, AuditLogRepository auditLogRepository,
                        ItemStatusPolicy policy, ItemService itemService, ClaimService claimService,
                        UserService userService, AuditService auditService) {
        this.itemRepository = itemRepository;
        this.claimRepository = claimRepository;
        this.userRepository = userRepository;
        this.auditLogRepository = auditLogRepository;
        this.policy = policy;
        this.itemService = itemService;
        this.claimService = claimService;
        this.userService = userService;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public AdminDashboardResponse dashboard(AppUserPrincipal admin) {
        DashboardCounts counts = new DashboardCounts(
                itemRepository.countByTypeAndStatus(ItemType.LOST, ItemStatus.OPEN),
                itemRepository.countByTypeAndStatus(ItemType.FOUND, ItemStatus.OPEN),
                claimRepository.countByStatus(ClaimStatus.PENDING),
                claimRepository.countByStatus(ClaimStatus.APPROVED),
                itemRepository.countByStatus(ItemStatus.RETURNED),
                itemRepository.countByStatus(ItemStatus.CLOSED),
                itemRepository.count(),
                userRepository.count());

        Page<Claim> pending = claimRepository.findAll(
                (root, query, cb) -> cb.equal(root.get("status"), ClaimStatus.PENDING),
                PageRequest.of(0, DASHBOARD_LIST_SIZE, Sort.by(Sort.Order.asc("createdAt"))));

        Page<Item> recent = itemRepository.findAll(PageRequest.of(0, DASHBOARD_LIST_SIZE,
                Sort.by(Sort.Order.desc("updatedAt"), Sort.Order.desc("id"))));

        return new AdminDashboardResponse(counts,
                pending.getContent().stream().map(claim -> claimService.toResponse(claim, admin)).toList(),
                recent.getContent().stream().map(item -> itemService.toResponse(item, admin)).toList());
    }

    @Transactional
    public ItemResponse moderate(Long itemId, ModerateItemRequest request, AppUserPrincipal admin) {
        requireAdmin(admin);
        Item item = itemService.getEntity(itemId);

        ItemStatus target = switch (request.action()) {
            case CLOSE -> ItemStatus.CLOSED;
            case REJECT -> ItemStatus.REJECTED;
            case REOPEN -> ItemStatus.OPEN;
        };

        if (request.action() == ModerateItemRequest.ModerationAction.REJECT
                && (request.reason() == null || request.reason().isBlank())) {
            throw new BadRequestException("A reason is required when rejecting a report");
        }
        if (!policy.isTransitionAllowed(item.getStatus(), target)) {
            throw new ConflictException("A report with status " + item.getStatus()
                    + " cannot be changed to " + target);
        }

        item.setStatus(target);
        item.setModerationReason(request.reason() == null ? null : request.reason().trim());
        if (target == ItemStatus.CLOSED) {
            item.setClosedAt(Instant.now());
        }
        if (target == ItemStatus.OPEN) {
            item.setClosedAt(null);
        }
        Item saved = itemRepository.save(item);
        auditService.record(admin.getId(), "ITEM_MODERATED_" + request.action().name(), "ITEM", saved.getId(),
                Map.of("status", target.name()));
        return itemService.toResponse(saved, admin);
    }

    /**
     * Marks a found item as returned. An approved claim is required unless the
     * administrator documents a valid exception.
     */
    @Transactional
    public ItemResponse markReturned(Long itemId, MarkReturnedRequest request, AppUserPrincipal admin) {
        requireAdmin(admin);
        Item item = itemService.getEntity(itemId);

        if (item.getType() != ItemType.FOUND) {
            throw new BadRequestException("Only found reports can be marked as returned");
        }
        if (!policy.isTransitionAllowed(item.getStatus(), ItemStatus.RETURNED)) {
            throw new ConflictException("A report with status " + item.getStatus() + " cannot be marked as returned");
        }

        boolean approvedClaimExists = claimRepository.existsByItemIdAndStatus(item.getId(), ClaimStatus.APPROVED);
        if (!approvedClaimExists && (request.reason() == null || request.reason().isBlank())) {
            throw new BadRequestException(
                    "An approved claim is required to mark an item as returned. "
                            + "Without one, please document the reason for this exception.");
        }

        item.setStatus(ItemStatus.RETURNED);
        item.setReturnedAt(Instant.now());
        item.setReturnedBy(userService.getById(admin.getId()));
        Item saved = itemRepository.save(item);

        // Remaining pending claims are closed because the item was handed over.
        for (Claim pending : claimRepository.findAll((root, query, cb) -> cb.and(
                cb.equal(root.get("item").get("id"), item.getId()),
                cb.equal(root.get("status"), ClaimStatus.PENDING)))) {
            pending.setStatus(ClaimStatus.REJECTED);
            pending.setReviewedBy(userService.getById(admin.getId()));
            pending.setReviewedAt(Instant.now());
            pending.setReviewReason("The item was already returned to its owner");
            claimRepository.save(pending);
        }

        auditService.record(admin.getId(), "ITEM_MARKED_RETURNED", "ITEM", saved.getId(),
                Map.of("approvedClaim", String.valueOf(approvedClaimExists)));
        return itemService.toResponse(saved, admin);
    }

    @Transactional(readOnly = true)
    public PageResponse<AuditLogResponse> auditLogs(int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
        Page<AuditLog> result = auditLogRepository.findAllByOrderByCreatedAtDesc(pageable);
        return PageResponse.of(result, log -> new AuditLogResponse(
                log.getId(), log.getActorId(), log.getAction(), log.getEntityType(), log.getEntityId(),
                log.getCreatedAt(), log.getMetadata()));
    }

    private void requireAdmin(AppUserPrincipal viewer) {
        if (viewer == null || !viewer.isAdmin()) {
            throw new ForbiddenException("Administrator privileges are required");
        }
    }
}
