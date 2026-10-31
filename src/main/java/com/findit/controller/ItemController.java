package com.findit.controller;

import com.findit.domain.ItemStatus;
import com.findit.domain.ItemType;
import com.findit.dto.PageResponse;
import com.findit.dto.claim.ClaimCreateRequest;
import com.findit.dto.claim.ClaimResponse;
import com.findit.dto.item.ItemCreateRequest;
import com.findit.dto.item.ItemPrivateDetailsResponse;
import com.findit.dto.item.ItemResponse;
import com.findit.dto.item.ItemSearchCriteria;
import com.findit.dto.item.ItemUpdateRequest;
import com.findit.dto.match.MatchResponse;
import com.findit.security.AppUserPrincipal;
import com.findit.service.ClaimService;
import com.findit.service.ImageService;
import com.findit.service.ItemService;
import com.findit.service.MatchService;
import jakarta.validation.Valid;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Lost and found reports.
 */
@RestController
@RequestMapping("/api/items")
public class ItemController {

    private final ItemService itemService;
    private final MatchService matchService;
    private final ClaimService claimService;
    private final ImageService imageService;

    public ItemController(ItemService itemService, MatchService matchService, ClaimService claimService,
                           ImageService imageService) {
        this.itemService = itemService;
        this.matchService = matchService;
        this.claimService = claimService;
        this.imageService = imageService;
    }

    @PostMapping
    public ResponseEntity<ItemResponse> create(@AuthenticationPrincipal AppUserPrincipal principal,
                                                @Valid @RequestBody ItemCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(itemService.create(request, principal));
    }

    @GetMapping
    public PageResponse<ItemResponse> search(@AuthenticationPrincipal AppUserPrincipal principal,
                                             @RequestParam(required = false) ItemType type,
                                             @RequestParam(required = false) Long categoryId,
                                             @RequestParam(required = false) Long locationId,
                                             @RequestParam(required = false) ItemStatus status,
                                             @RequestParam(required = false) LocalDate dateFrom,
                                             @RequestParam(required = false) LocalDate dateTo,
                                             @RequestParam(required = false) String q,
                                             @RequestParam(required = false) Integer page,
                                             @RequestParam(required = false) Integer size,
                                             @RequestParam(required = false) String sort,
                                             @RequestParam(required = false) Boolean mine) {
        ItemSearchCriteria criteria = new ItemSearchCriteria(type, categoryId, locationId, status, dateFrom, dateTo,
                q, page, size, sort, mine, null);
        return itemService.search(criteria, principal);
    }

    @GetMapping("/{id}")
    public ItemResponse get(@AuthenticationPrincipal AppUserPrincipal principal, @PathVariable Long id) {
        return itemService.get(id, principal);
    }

    @PutMapping("/{id}")
    public ItemResponse update(@AuthenticationPrincipal AppUserPrincipal principal,
                                @PathVariable Long id, @Valid @RequestBody ItemUpdateRequest request) {
        return itemService.update(id, request, principal);
    }

    @PostMapping("/{id}/close")
    public ItemResponse close(@AuthenticationPrincipal AppUserPrincipal principal, @PathVariable Long id) {
        return itemService.close(id, principal);
    }

    @GetMapping("/{id}/matches")
    public List<MatchResponse> matches(@AuthenticationPrincipal AppUserPrincipal principal, @PathVariable Long id) {
        return matchService.findMatches(id, principal);
    }

    @PostMapping("/{id}/claims")
    public ResponseEntity<ClaimResponse> claim(@AuthenticationPrincipal AppUserPrincipal principal,
                                               @PathVariable Long id,
                                               @Valid @RequestBody ClaimCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(claimService.submit(id, request, principal));
    }

    /**
     * Private part of a report - owner and administrators only.
     */
    @GetMapping("/{id}/private-details")
    public ItemPrivateDetailsResponse privateDetails(@AuthenticationPrincipal AppUserPrincipal principal,
                                                      @PathVariable Long id) {
        return itemService.getPrivateDetails(id, principal);
    }

    @PostMapping(path = "/{id}/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ItemResponse uploadImage(@AuthenticationPrincipal AppUserPrincipal principal, @PathVariable Long id,
                                    @RequestParam("file") MultipartFile file) {
        return itemService.uploadImage(id, file, principal);
    }

    @GetMapping("/{id}/image")
    public ResponseEntity<Resource> image(@AuthenticationPrincipal AppUserPrincipal principal,
                                          @PathVariable Long id) {
        Resource resource = itemService.loadImage(id, principal);
        String contentType = itemService.imageContentType(id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .cacheControl(CacheControl.maxAge(10, TimeUnit.MINUTES).cachePrivate())
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                .body(resource);
    }
}
