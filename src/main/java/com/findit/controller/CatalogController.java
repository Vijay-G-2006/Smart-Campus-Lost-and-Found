package com.findit.controller;

import com.findit.domain.CampusLocation;
import com.findit.domain.Category;
import com.findit.repository.CampusLocationRepository;
import com.findit.repository.CategoryRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Reference data: item categories and campus locations.
 */
@RestController
@RequestMapping("/api")
public class CatalogController {

    private final CategoryRepository categoryRepository;
    private final CampusLocationRepository locationRepository;

    public CatalogController(CategoryRepository categoryRepository, CampusLocationRepository locationRepository) {
        this.categoryRepository = categoryRepository;
        this.locationRepository = locationRepository;
    }

    @GetMapping("/categories")
    public List<Map<String, Object>> categories() {
        return categoryRepository.findByActiveTrueOrderByNameAsc().stream()
                .map(this::toCategory)
                .toList();
    }

    @GetMapping("/locations")
    public List<Map<String, Object>> locations() {
        return locationRepository.findByActiveTrueOrderByNameAsc().stream()
                .map(this::toLocation)
                .toList();
    }

    private Map<String, Object> toCategory(Category category) {
        return Map.of("id", category.getId(), "name", category.getName(),
                "description", category.getDescription() == null ? "" : category.getDescription());
    }

    private Map<String, Object> toLocation(CampusLocation location) {
        return Map.of("id", location.getId(), "name", location.getName(),
                "description", location.getDescription() == null ? "" : location.getDescription());
    }
}
