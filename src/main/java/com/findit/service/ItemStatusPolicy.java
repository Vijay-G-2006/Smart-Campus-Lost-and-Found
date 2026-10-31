package com.findit.service;

import com.findit.domain.Item;
import com.findit.domain.ItemStatus;
import com.findit.domain.ItemType;
import com.findit.security.AppUserPrincipal;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * The single place that defines which status transitions are allowed and who
 * may trigger them. The controllers never decide this on their own.
 */
@Component
public class ItemStatusPolicy {

    private static final Map<ItemStatus, Set<ItemStatus>> ALLOWED_TRANSITIONS = Map.of(
            ItemStatus.OPEN, EnumSet.of(ItemStatus.UNDER_REVIEW, ItemStatus.CLAIMED, ItemStatus.RETURNED,
                    ItemStatus.CLOSED, ItemStatus.REJECTED),
            ItemStatus.UNDER_REVIEW, EnumSet.of(ItemStatus.OPEN, ItemStatus.CLAIMED, ItemStatus.RETURNED,
                    ItemStatus.CLOSED, ItemStatus.REJECTED),
            ItemStatus.CLAIMED, EnumSet.of(ItemStatus.RETURNED, ItemStatus.CLOSED, ItemStatus.REJECTED),
            ItemStatus.CLOSED, EnumSet.of(ItemStatus.OPEN),
            ItemStatus.REJECTED, EnumSet.of(ItemStatus.OPEN),
            ItemStatus.RETURNED, EnumSet.noneOf(ItemStatus.class));

    public boolean isTransitionAllowed(ItemStatus from, ItemStatus to) {
        return ALLOWED_TRANSITIONS.getOrDefault(from, Set.of()).contains(to);
    }

    public boolean isOwner(Item item, AppUserPrincipal viewer) {
        return viewer != null && item.getCreatedBy() != null
                && item.getCreatedBy().getId().equals(viewer.getId());
    }

    /**
     * Owners may edit their report while it is still open.
     */
    public boolean canEdit(Item item, AppUserPrincipal viewer) {
        return isOwner(item, viewer) && item.getStatus() == ItemStatus.OPEN;
    }

    /**
     * Owners may close their own report; administrators may close any report.
     */
    public boolean canClose(Item item, AppUserPrincipal viewer) {
        if (viewer == null) {
            return false;
        }
        if (item.getStatus() == ItemStatus.RETURNED) {
            return false;
        }
        return isOwner(item, viewer) || viewer.isAdmin();
    }

    /**
     * Only found items that are not returned, closed or rejected can be
     * claimed. Administrators review claims, they do not file them.
     */
    public boolean canClaim(Item item, AppUserPrincipal viewer) {
        if (viewer == null || viewer.isAdmin()) {
            return false;
        }
        if (item.getType() != ItemType.FOUND) {
            return false;
        }
        if (isOwner(item, viewer)) {
            return false;
        }
        return item.getStatus() == ItemStatus.OPEN || item.getStatus() == ItemStatus.UNDER_REVIEW;
    }

    /**
     * An item that has an approved claim can no longer take new claims.
     */
    public boolean acceptsNewClaims(Item item) {
        return item.getType() == ItemType.FOUND
                && (item.getStatus() == ItemStatus.OPEN || item.getStatus() == ItemStatus.UNDER_REVIEW);
    }
}
