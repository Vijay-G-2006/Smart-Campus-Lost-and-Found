package com.findit.dto.item;

import com.findit.domain.ItemStatus;
import com.findit.domain.ItemType;

import java.time.LocalDate;

/**
 * Search and filter criteria for the item list endpoint.
 */
public record ItemSearchCriteria(
        ItemType type,
        Long categoryId,
        Long locationId,
        ItemStatus status,
        LocalDate dateFrom,
        LocalDate dateTo,
        String keyword,
        Integer page,
        Integer size,
        String sort,
        Boolean mine,
        Long ownerId
) {

    public static final int DEFAULT_SIZE = 12;
    public static final int MAX_SIZE = 50;

    public int pageOrDefault() {
        return page == null || page < 0 ? 0 : page;
    }

    public int sizeOrDefault() {
        if (size == null || size < 1) {
            return DEFAULT_SIZE;
        }
        return Math.min(size, MAX_SIZE);
    }
}
