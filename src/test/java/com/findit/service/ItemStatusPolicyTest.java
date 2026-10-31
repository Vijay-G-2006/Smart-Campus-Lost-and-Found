package com.findit.service;

import com.findit.domain.CampusLocation;
import com.findit.domain.Category;
import com.findit.domain.Item;
import com.findit.domain.ItemStatus;
import com.findit.domain.ItemType;
import com.findit.domain.Role;
import com.findit.domain.User;
import com.findit.security.AppUserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the status and permission rules (PRD FR-03, FR-07, FR-08).
 */
class ItemStatusPolicyTest {

    private ItemStatusPolicy policy;
    private User owner;
    private User other;
    private User admin;

    @BeforeEach
    void setUp() {
        policy = new ItemStatusPolicy();

        owner = user(1L, Role.USER);
        other = user(2L, Role.USER);
        admin = user(3L, Role.ADMIN);
    }

    private User user(Long id, Role role) {
        User user = new User("user" + id + "@findit.local", "User " + id, "hash", role);
        user.setId(id);
        return user;
    }

    private AppUserPrincipal principal(User user) {
        return new AppUserPrincipal(user.getId(), user.getEmail(), user.getDisplayName(), "hash",
                user.getRole(), true);
    }

    private Item item(ItemType type, User creator, ItemStatus status) {
        Item item = new Item();
        item.setType(type);
        item.setStatus(status);
        item.setCreatedBy(creator);
        item.setTitle("Test item");
        item.setDescription("A test item description");
        Category category = new Category("Electronics", null);
        category.setId(1L);
        item.setCategory(category);
        CampusLocation location = new CampusLocation("Main Library", null);
        location.setId(1L);
        item.setLocation(location);
        return item;
    }

    @Test
    @DisplayName("the owner may edit an open report, but not after it was closed or returned")
    void editPermissions() {
        Item open = item(ItemType.LOST, owner, ItemStatus.OPEN);
        Item underReview = item(ItemType.FOUND, owner, ItemStatus.UNDER_REVIEW);
        Item returned = item(ItemType.FOUND, owner, ItemStatus.RETURNED);

        assertThat(policy.canEdit(open, principal(owner))).isTrue();
        assertThat(policy.canEdit(underReview, principal(owner))).isFalse();
        assertThat(policy.canEdit(returned, principal(owner))).isFalse();
        assertThat(policy.canEdit(open, principal(other))).isFalse();
        assertThat(policy.canEdit(open, principal(admin))).isFalse();
    }

    @Test
    @DisplayName("the owner may close an open report, an administrator may close any report")
    void closePermissions() {
        Item open = item(ItemType.LOST, owner, ItemStatus.OPEN);
        Item returned = item(ItemType.FOUND, owner, ItemStatus.RETURNED);

        assertThat(policy.canClose(open, principal(owner))).isTrue();
        assertThat(policy.canClose(open, principal(admin))).isTrue();
        assertThat(policy.canClose(open, principal(other))).isFalse();
        assertThat(policy.canClose(returned, principal(admin))).isFalse();
    }

    @Test
    @DisplayName("only found items that are not returned can be claimed")
    void claimPermissions() {
        Item foundOpen = item(ItemType.FOUND, owner, ItemStatus.OPEN);
        Item foundUnderReview = item(ItemType.FOUND, owner, ItemStatus.UNDER_REVIEW);
        Item foundClaimed = item(ItemType.FOUND, owner, ItemStatus.CLAIMED);
        Item foundReturned = item(ItemType.FOUND, owner, ItemStatus.RETURNED);
        Item foundClosed = item(ItemType.FOUND, owner, ItemStatus.CLOSED);
        Item lost = item(ItemType.LOST, owner, ItemStatus.OPEN);

        assertThat(policy.canClaim(foundOpen, principal(other))).isTrue();
        assertThat(policy.canClaim(foundUnderReview, principal(other))).isTrue();
        assertThat(policy.canClaim(foundClaimed, principal(other))).isFalse();
        assertThat(policy.canClaim(foundReturned, principal(other))).isFalse();
        assertThat(policy.canClaim(foundClosed, principal(other))).isFalse();
        assertThat(policy.canClaim(lost, principal(other))).isFalse();
    }

    @Test
    @DisplayName("nobody can claim their own report, administrators do not file claims")
    void ownReportCannotBeClaimed() {
        Item found = item(ItemType.FOUND, owner, ItemStatus.OPEN);

        assertThat(policy.canClaim(found, principal(owner))).isFalse();
        assertThat(policy.canClaim(found, principal(admin))).isFalse();
    }

    @Test
    @DisplayName("the allowed status transitions are enforced")
    void allowedTransitions() {
        assertThat(policy.isTransitionAllowed(ItemStatus.OPEN, ItemStatus.UNDER_REVIEW)).isTrue();
        assertThat(policy.isTransitionAllowed(ItemStatus.OPEN, ItemStatus.CLAIMED)).isTrue();
        assertThat(policy.isTransitionAllowed(ItemStatus.UNDER_REVIEW, ItemStatus.CLAIMED)).isTrue();
        assertThat(policy.isTransitionAllowed(ItemStatus.CLAIMED, ItemStatus.RETURNED)).isTrue();
        assertThat(policy.isTransitionAllowed(ItemStatus.CLOSED, ItemStatus.OPEN)).isTrue();
        assertThat(policy.isTransitionAllowed(ItemStatus.REJECTED, ItemStatus.OPEN)).isTrue();
    }

    @Test
    @DisplayName("a returned item is final and closed reports cannot be claimed again")
    void terminalStates() {
        assertThat(policy.isTransitionAllowed(ItemStatus.RETURNED, ItemStatus.OPEN)).isFalse();
        assertThat(policy.isTransitionAllowed(ItemStatus.RETURNED, ItemStatus.CLOSED)).isFalse();
        assertThat(policy.isTransitionAllowed(ItemStatus.CLOSED, ItemStatus.CLAIMED)).isFalse();
        assertThat(policy.acceptsNewClaims(item(ItemType.FOUND, owner, ItemStatus.CLOSED))).isFalse();
        assertThat(policy.acceptsNewClaims(item(ItemType.FOUND, owner, ItemStatus.RETURNED))).isFalse();
        assertThat(policy.acceptsNewClaims(item(ItemType.FOUND, owner, ItemStatus.OPEN))).isTrue();
    }
}
