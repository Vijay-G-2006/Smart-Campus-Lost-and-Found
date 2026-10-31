package com.findit.service;

import com.findit.domain.CampusLocation;
import com.findit.domain.Category;
import com.findit.domain.Item;
import com.findit.domain.ItemStatus;
import com.findit.domain.ItemType;
import com.findit.domain.Role;
import com.findit.domain.User;
import com.findit.dto.PageResponse;
import com.findit.dto.item.ItemCreateRequest;
import com.findit.dto.item.ItemPrivateDetailsResponse;
import com.findit.dto.item.ItemResponse;
import com.findit.dto.item.ItemSearchCriteria;
import com.findit.dto.item.ItemUpdateRequest;
import com.findit.exception.BadRequestException;
import com.findit.exception.ForbiddenException;
import com.findit.exception.NotFoundException;
import com.findit.repository.CampusLocationRepository;
import com.findit.repository.CategoryRepository;
import com.findit.repository.ItemRepository;
import com.findit.security.AppUserPrincipal;
import jakarta.persistence.criteria.Predicate;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * All business rules around lost and found reports.
 */
@Service
public class ItemService {

    private final ItemRepository itemRepository;
    private final CategoryRepository categoryRepository;
    private final CampusLocationRepository locationRepository;
    private final ItemStatusPolicy policy;
    private final ImageService imageService;
    private final AuditService auditService;
    private final UserService userService;

    public ItemService(ItemRepository itemRepository, CategoryRepository categoryRepository,
                       CampusLocationRepository locationRepository, ItemStatusPolicy policy,
                       ImageService imageService, AuditService auditService, UserService userService) {
        this.itemRepository = itemRepository;
        this.categoryRepository = categoryRepository;
        this.locationRepository = locationRepository;
        this.policy = policy;
        this.imageService = imageService;
        this.auditService = auditService;
        this.userService = userService;
    }

    // ------------------------------------------------------------------ read

    @Transactional(readOnly = true)
    public Item getEntity(Long id) {
        return itemRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Report not found"));
    }

    @Transactional(readOnly = true)
    public ItemResponse get(Long id, AppUserPrincipal viewer) {
        Item item = getEntity(id);
        if (!isVisibleTo(item, viewer)) {
            // Do not leak the existence of moderated reports.
            throw new NotFoundException("Report not found");
        }
        return toResponse(item, viewer);
    }

    @Transactional(readOnly = true)
    public PageResponse<ItemResponse> search(ItemSearchCriteria criteria, AppUserPrincipal viewer) {
        Specification<Item> specification = buildSpecification(criteria, viewer);
        Pageable pageable = PageRequest.of(criteria.pageOrDefault(), criteria.sizeOrDefault(), resolveSort(criteria.sort()));
        Page<Item> page = itemRepository.findAll(specification, pageable);
        return PageResponse.of(page, item -> toResponse(item, viewer));
    }

    @Transactional(readOnly = true)
    public PageResponse<ItemResponse> myReports(AppUserPrincipal viewer, int page, int size) {
        int pageIndex = Math.max(page, 0);
        int pageSize = Math.min(Math.max(size, 1), 50);
        ItemSearchCriteria criteria = new ItemSearchCriteria(null, null, null, null, null, null, null,
                pageIndex, pageSize, "newest", Boolean.TRUE, null);
        Pageable pageable = PageRequest.of(pageIndex, pageSize, resolveSort(criteria.sort()));
        Page<Item> result = itemRepository.findAll(buildSpecification(criteria, viewer), pageable);
        return PageResponse.of(result, item -> toResponse(item, viewer));
    }

    /**
     * Private details of a report - only the owner and administrators may read them.
     */
    @Transactional(readOnly = true)
    public ItemPrivateDetailsResponse getPrivateDetails(Long id, AppUserPrincipal viewer) {
        Item item = getEntity(id);
        boolean allowed = policy.isOwner(item, viewer) || (viewer != null && viewer.isAdmin());
        if (!allowed) {
            throw new ForbiddenException("You may only read the private details of your own report");
        }
        User owner = item.getCreatedBy();
        return new ItemPrivateDetailsResponse(item.getId(), item.getPrivateVerificationDetail(),
                owner.getContactChannel(), owner.getEmail());
    }

    // ----------------------------------------------------------------- write

    @Transactional
    public ItemResponse create(ItemCreateRequest request, AppUserPrincipal viewer) {
        Item item = new Item();
        item.setType(request.type());
        item.setTitle(request.title().trim());
        item.setDescription(request.description().trim());
        item.setCategory(resolveCategory(request.categoryId()));
        item.setLocation(resolveLocation(request.locationId()));
        item.setEventDate(request.eventDate());
        item.setStatus(ItemStatus.OPEN);
        item.setCreatedBy(userService.getById(viewer.getId()));
        item.setPrivateVerificationDetail(normaliseOptional(request.privateVerificationDetail()));

        Item saved = itemRepository.save(item);
        auditService.record(viewer.getId(), "ITEM_CREATED", "ITEM", saved.getId(),
                java.util.Map.of("type", saved.getType().name()));
        return toResponse(saved, viewer);
    }

    @Transactional
    public ItemResponse update(Long id, ItemUpdateRequest request, AppUserPrincipal viewer) {
        Item item = getEntity(id);
        if (!policy.canEdit(item, viewer)) {
            throw new ForbiddenException("You can only edit your own report while it is still open");
        }
        item.setTitle(request.title().trim());
        item.setDescription(request.description().trim());
        item.setCategory(resolveCategory(request.categoryId()));
        item.setLocation(resolveLocation(request.locationId()));
        item.setEventDate(request.eventDate());
        item.setPrivateVerificationDetail(normaliseOptional(request.privateVerificationDetail()));

        Item saved = itemRepository.save(item);
        auditService.record(viewer.getId(), "ITEM_UPDATED", "ITEM", saved.getId(), null);
        return toResponse(saved, viewer);
    }

    @Transactional
    public ItemResponse close(Long id, AppUserPrincipal viewer) {
        Item item = getEntity(id);
        if (!policy.canClose(item, viewer)) {
            throw new ForbiddenException("You cannot close this report");
        }
        if (item.getStatus() == ItemStatus.CLOSED) {
            return toResponse(item, viewer);
        }
        if (!policy.isTransitionAllowed(item.getStatus(), ItemStatus.CLOSED)) {
            throw new BadRequestException("A report with status " + item.getStatus() + " cannot be closed");
        }
        item.setStatus(ItemStatus.CLOSED);
        item.setClosedAt(Instant.now());
        Item saved = itemRepository.save(item);
        auditService.record(viewer.getId(), "ITEM_CLOSED", "ITEM", saved.getId(), null);
        return toResponse(saved, viewer);
    }

    @Transactional
    public ItemResponse uploadImage(Long id, MultipartFile file, AppUserPrincipal viewer) {
        Item item = getEntity(id);
        if (!policy.isOwner(item, viewer)) {
            throw new ForbiddenException("Only the owner can upload an image to this report");
        }
        if (item.getStatus() != ItemStatus.OPEN) {
            throw new BadRequestException("Images can only be changed while the report is open");
        }
        ImageService.StoredImage stored = imageService.store(file);
        String previous = item.getImageBlobName();
        item.setImageBlobName(stored.storageName());
        item.setImageContentType(stored.contentType());
        Item saved = itemRepository.save(item);
        imageService.delete(previous);
        auditService.record(viewer.getId(), "ITEM_IMAGE_UPLOADED", "ITEM", saved.getId(), null);
        return toResponse(saved, viewer);
    }

    @Transactional(readOnly = true)
    public Resource loadImage(Long id, AppUserPrincipal viewer) {
        Item item = getEntity(id);
        if (!isVisibleTo(item, viewer)) {
            throw new NotFoundException("Report not found");
        }
        if (item.getImageBlobName() == null) {
            throw new NotFoundException("This report has no image");
        }
        return imageService.load(item.getImageBlobName());
    }

    @Transactional(readOnly = true)
    public String imageContentType(Long id) {
        Item item = getEntity(id);
        return item.getImageContentType() == null ? "application/octet-stream" : item.getImageContentType();
    }

    // --------------------------------------------------------------- helpers

    @Transactional(readOnly = true)
    public boolean isVisibleTo(Item item, AppUserPrincipal viewer) {
        if (item.getStatus() != ItemStatus.REJECTED) {
            return true;
        }
        if (viewer == null) {
            return false;
        }
        return viewer.isAdmin() || policy.isOwner(item, viewer);
    }

    @Transactional(readOnly = true)
    public ItemResponse toResponse(Item item, AppUserPrincipal viewer) {
        boolean owner = policy.isOwner(item, viewer);
        boolean admin = viewer != null && viewer.isAdmin();
        return new ItemResponse(
                item.getId(),
                item.getType(),
                item.getTitle(),
                item.getDescription(),
                item.getCategory().getId(),
                item.getCategory().getName(),
                item.getLocation().getId(),
                item.getLocation().getName(),
                item.getEventDate(),
                item.getStatus(),
                item.getCreatedBy().getId(),
                item.getCreatedBy().getDisplayName(),
                item.getCreatedAt(),
                item.getUpdatedAt(),
                item.getImageBlobName() != null,
                item.getImageBlobName() != null ? "/api/items/" + item.getId() + "/image" : null,
                item.getReturnedAt(),
                item.getClosedAt(),
                owner,
                policy.canEdit(item, viewer),
                policy.canClose(item, viewer),
                policy.canClaim(item, viewer) && !admin);
    }

    private Specification<Item> buildSpecification(ItemSearchCriteria criteria, AppUserPrincipal viewer) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (criteria.type() != null) {
                predicates.add(cb.equal(root.get("type"), criteria.type()));
            }
            if (criteria.categoryId() != null) {
                predicates.add(cb.equal(root.get("category").get("id"), criteria.categoryId()));
            }
            if (criteria.locationId() != null) {
                predicates.add(cb.equal(root.get("location").get("id"), criteria.locationId()));
            }
            if (criteria.status() != null) {
                predicates.add(cb.equal(root.get("status"), criteria.status()));
            }
            if (criteria.dateFrom() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("eventDate"), criteria.dateFrom()));
            }
            if (criteria.dateTo() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("eventDate"), criteria.dateTo()));
            }
            if (criteria.ownerId() != null) {
                predicates.add(cb.equal(root.get("createdBy").get("id"), criteria.ownerId()));
            } else if (Boolean.TRUE.equals(criteria.mine()) && viewer != null) {
                predicates.add(cb.equal(root.get("createdBy").get("id"), viewer.getId()));
            }
            if (criteria.keyword() != null && !criteria.keyword().isBlank()) {
                String pattern = "%" + criteria.keyword().trim().toLowerCase(Locale.ROOT) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("title")), pattern),
                        cb.like(cb.lower(root.get("description")), pattern)));
            }
            // Moderated (rejected) reports are only visible to their owner and to admins.
            if (viewer == null || !viewer.isAdmin()) {
                Predicate notRejected = cb.notEqual(root.get("status"), ItemStatus.REJECTED);
                if (viewer != null) {
                    predicates.add(cb.or(notRejected, cb.equal(root.get("createdBy").get("id"), viewer.getId())));
                } else {
                    predicates.add(notRejected);
                }
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private Sort resolveSort(String sort) {
        String value = sort == null ? "newest" : sort.trim().toLowerCase(Locale.ROOT);
        return switch (value) {
            case "oldest" -> Sort.by(Sort.Order.asc("createdAt"), Sort.Order.asc("id"));
            case "event-date" -> Sort.by(Sort.Order.desc("eventDate"), Sort.Order.desc("id"));
            case "event-date-asc" -> Sort.by(Sort.Order.asc("eventDate"), Sort.Order.asc("id"));
            case "title" -> Sort.by(Sort.Order.asc("title"), Sort.Order.asc("id"));
            default -> Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));
        };
    }

    private Category resolveCategory(Long id) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new BadRequestException("Unknown category"));
        if (!category.isActive()) {
            throw new BadRequestException("This category is not available");
        }
        return category;
    }

    private CampusLocation resolveLocation(Long id) {
        CampusLocation location = locationRepository.findById(id)
                .orElseThrow(() -> new BadRequestException("Unknown campus location"));
        if (!location.isActive()) {
            throw new BadRequestException("This campus location is not available");
        }
        return location;
    }

    private String normaliseOptional(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() > 1000 ? trimmed.substring(0, 1000) : trimmed;
    }

    /**
     * Helper used by the administrator views.
     */
    public boolean isAdmin(AppUserPrincipal viewer) {
        return viewer != null && viewer.getRole() == Role.ADMIN;
    }
}
