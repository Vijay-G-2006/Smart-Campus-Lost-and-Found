package com.findit.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * A private ownership claim on a found item.
 *
 * <p>{@code explanation} and {@code verificationAnswers} contain confidential
 * information and are only ever returned to the claimant and to administrators.</p>
 */
@Entity
@Table(name = "claims", indexes = {
        @Index(name = "idx_claims_item", columnList = "item_id"),
        @Index(name = "idx_claims_claimant", columnList = "claimant_id"),
        @Index(name = "idx_claims_status", columnList = "status")
})
public class Claim extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "item_id", nullable = false)
    private Item item;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "claimant_id", nullable = false)
    private User claimant;

    /** Private explanation written by the claimant. */
    @Column(name = "explanation", nullable = false, length = 2000)
    private String explanation;

    /** Private verification answers written by the claimant. */
    @Column(name = "verification_answers", length = 1000)
    private String verificationAnswers;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ClaimStatus status = ClaimStatus.PENDING;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by")
    private User reviewedBy;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "review_reason", length = 1000)
    private String reviewReason;

    @Column(name = "withdrawn_at")
    private Instant withdrawnAt;

    protected Claim() {
        // for JPA
    }

    public Claim(Item item, User claimant, String explanation, String verificationAnswers) {
        this.item = item;
        this.claimant = claimant;
        this.explanation = explanation;
        this.verificationAnswers = verificationAnswers;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Item getItem() {
        return item;
    }

    public void setItem(Item item) {
        this.item = item;
    }

    public User getClaimant() {
        return claimant;
    }

    public void setClaimant(User claimant) {
        this.claimant = claimant;
    }

    public String getExplanation() {
        return explanation;
    }

    public void setExplanation(String explanation) {
        this.explanation = explanation;
    }

    public String getVerificationAnswers() {
        return verificationAnswers;
    }

    public void setVerificationAnswers(String verificationAnswers) {
        this.verificationAnswers = verificationAnswers;
    }

    public ClaimStatus getStatus() {
        return status;
    }

    public void setStatus(ClaimStatus status) {
        this.status = status;
    }

    public User getReviewedBy() {
        return reviewedBy;
    }

    public void setReviewedBy(User reviewedBy) {
        this.reviewedBy = reviewedBy;
    }

    public Instant getReviewedAt() {
        return reviewedAt;
    }

    public void setReviewedAt(Instant reviewedAt) {
        this.reviewedAt = reviewedAt;
    }

    public String getReviewReason() {
        return reviewReason;
    }

    public void setReviewReason(String reviewReason) {
        this.reviewReason = reviewReason;
    }

    public Instant getWithdrawnAt() {
        return withdrawnAt;
    }

    public void setWithdrawnAt(Instant withdrawnAt) {
        this.withdrawnAt = withdrawnAt;
    }
}
