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
import java.time.LocalDate;

/**
 * A lost or found item report.
 *
 * <p>{@code privateVerificationDetail} is confidential: it is only ever returned
 * by explicitly private endpoints (owner / administrator), never by the public
 * item endpoints.</p>
 */
@Entity
@Table(name = "items", indexes = {
        @Index(name = "idx_items_type_status", columnList = "type,status"),
        @Index(name = "idx_items_category", columnList = "category_id"),
        @Index(name = "idx_items_location", columnList = "location_id"),
        @Index(name = "idx_items_event_date", columnList = "event_date"),
        @Index(name = "idx_items_created_by", columnList = "created_by"),
        @Index(name = "idx_items_created_at", columnList = "created_at")
})
public class Item extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 10)
    private ItemType type;

    @Column(name = "title", nullable = false, length = 100)
    private String title;

    @Column(name = "description", nullable = false, length = 2000)
    private String description;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "location_id", nullable = false)
    private CampusLocation location;

    /** Approximate date of the loss / find event (not the report date). */
    @Column(name = "event_date", nullable = false)
    private LocalDate eventDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ItemStatus status = ItemStatus.OPEN;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    /** Opaque, server generated name of the stored image (never a user file name). */
    @Column(name = "image_blob_name", length = 200)
    private String imageBlobName;

    @Column(name = "image_content_type", length = 100)
    private String imageContentType;

    /** Private, non-public identifying detail used for manual verification. */
    @Column(name = "private_verification_detail", length = 1000)
    private String privateVerificationDetail;

    @Column(name = "returned_at")
    private Instant returnedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "returned_by")
    private User returnedBy;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "moderation_reason", length = 500)
    private String moderationReason;

    public Item() {
        // for JPA and for programmatic creation in the service layer
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public ItemType getType() {
        return type;
    }

    public void setType(ItemType type) {
        this.type = type;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Category getCategory() {
        return category;
    }

    public void setCategory(Category category) {
        this.category = category;
    }

    public CampusLocation getLocation() {
        return location;
    }

    public void setLocation(CampusLocation location) {
        this.location = location;
    }

    public LocalDate getEventDate() {
        return eventDate;
    }

    public void setEventDate(LocalDate eventDate) {
        this.eventDate = eventDate;
    }

    public ItemStatus getStatus() {
        return status;
    }

    public void setStatus(ItemStatus status) {
        this.status = status;
    }

    public User getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(User createdBy) {
        this.createdBy = createdBy;
    }

    public String getImageBlobName() {
        return imageBlobName;
    }

    public void setImageBlobName(String imageBlobName) {
        this.imageBlobName = imageBlobName;
    }

    public String getImageContentType() {
        return imageContentType;
    }

    public void setImageContentType(String imageContentType) {
        this.imageContentType = imageContentType;
    }

    public String getPrivateVerificationDetail() {
        return privateVerificationDetail;
    }

    public void setPrivateVerificationDetail(String privateVerificationDetail) {
        this.privateVerificationDetail = privateVerificationDetail;
    }

    public Instant getReturnedAt() {
        return returnedAt;
    }

    public void setReturnedAt(Instant returnedAt) {
        this.returnedAt = returnedAt;
    }

    public User getReturnedBy() {
        return returnedBy;
    }

    public void setReturnedBy(User returnedBy) {
        this.returnedBy = returnedBy;
    }

    public Instant getClosedAt() {
        return closedAt;
    }

    public void setClosedAt(Instant closedAt) {
        this.closedAt = closedAt;
    }

    public String getModerationReason() {
        return moderationReason;
    }

    public void setModerationReason(String moderationReason) {
        this.moderationReason = moderationReason;
    }
}
