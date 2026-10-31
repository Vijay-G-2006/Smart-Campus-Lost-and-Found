package com.findit.config;

import com.findit.security.AppUserPrincipal;
import com.findit.service.UserService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/**
 * Web layer configuration: narrow CORS rules and the location of the static
 * front-end files.
 *
 * <p>CORS is only enabled when explicit origins are configured. The default
 * (same origin) needs no CORS at all, which is the safest setup.</p>
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final AppProperties appProperties;

    public WebConfig(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        List<String> origins = appProperties.getCors().getAllowedOrigins();
        if (origins == null || origins.isEmpty()) {
            return;
        }
        registry.addMapping("/api/**")
                .allowedOrigins(origins.toArray(String[]::new))
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("Content-Type", "X-XSRF-TOKEN", "X-Requested-With", "Accept")
                .exposedHeaders("Location")
                .allowCredentials(true)
                .maxAge(3600);
    }

    /**
     * Small helper so that other components can look up the current user.
     */
    @Bean
    public CurrentUserLookup currentUserLookup(UserService userService) {
        return new CurrentUserLookup(userService);
    }

    /**
     * Looks up the signed in user.
     */
    public static class CurrentUserLookup {
        private final UserService userService;

        public CurrentUserLookup(UserService userService) {
            this.userService = userService;
        }

        public AppUserPrincipal require(com.findit.domain.User user) {
            return new AppUserPrincipal(user.getId(), user.getEmail(), user.getDisplayName(), null,
                    user.getRole(), user.isActive());
        }
    }
}
