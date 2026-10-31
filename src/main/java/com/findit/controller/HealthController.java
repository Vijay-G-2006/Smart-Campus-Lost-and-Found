package com.findit.controller;

import com.findit.service.ImageService;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Lightweight health endpoint for local checks and for the Azure App Service
 * health probe. It never contains secrets.
 */
@RestController
@RequestMapping("/api/health")
public class HealthController {

    private final ImageService imageService;
    private final Environment environment;

    public HealthController(ImageService imageService, Environment environment) {
        this.imageService = imageService;
        this.environment = environment;
    }

    @GetMapping
    public Map<String, Object> health() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "UP");
        body.put("application", "FindIT");
        body.put("profile", effectiveProfile());
        body.put("imageStorage", imageService.describeStorage());
        body.put("time", Instant.now().toString());
        return body;
    }

    /**
     * The active profile, or the default profile when none was activated.
     */
    private String effectiveProfile() {
        String[] active = environment.getActiveProfiles();
        if (active.length > 0) {
            return String.join(",", active);
        }
        String[] defaults = environment.getDefaultProfiles();
        return defaults.length > 0 ? String.join(",", defaults) : "none";
    }
}
