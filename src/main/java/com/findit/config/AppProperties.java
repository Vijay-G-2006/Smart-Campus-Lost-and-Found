package com.findit.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * Application configuration. Everything is bound from {@code application.yml},
 * environment variables or the {@code .env} file - no secret is hard coded.
 */
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private final Storage storage = new Storage();
    private final Seed seed = new Seed();
    private final Cors cors = new Cors();
    private final Security security = new Security();

    public Storage getStorage() {
        return storage;
    }

    public Seed getSeed() {
        return seed;
    }

    public Cors getCors() {
        return cors;
    }

    public Security getSecurity() {
        return security;
    }

    /**
     * Image storage settings. {@code type=local} keeps uploaded files on the
     * local disk, {@code type=azure} uses Azure Blob Storage.
     */
    public static class Storage {
        /** {@code local} or {@code azure}. */
        private String type = "local";
        /** Directory for local development uploads (kept outside the static paths). */
        private String localDir = "local-data/images";
        /** Azure Blob container name. The container is always private. */
        private String container = "findit-item-images";
        /** Azure Storage connection string (optional when managed identity is used). */
        private String connectionString;
        /** Azure Storage account name (used for managed identity access). */
        private String accountName;
        /** Maximum accepted upload size in bytes. */
        private long maxFileSizeBytes = 5L * 1024 * 1024;
        /** Accepted content types. */
        private List<String> allowedContentTypes = new ArrayList<>(List.of(
                "image/jpeg", "image/png", "image/webp", "image/gif"));

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getLocalDir() {
            return localDir;
        }

        public void setLocalDir(String localDir) {
            this.localDir = localDir;
        }

        public String getContainer() {
            return container;
        }

        public void setContainer(String container) {
            this.container = container;
        }

        public String getConnectionString() {
            return connectionString;
        }

        public void setConnectionString(String connectionString) {
            this.connectionString = connectionString;
        }

        public String getAccountName() {
            return accountName;
        }

        public void setAccountName(String accountName) {
            this.accountName = accountName;
        }

        public long getMaxFileSizeBytes() {
            return maxFileSizeBytes;
        }

        public void setMaxFileSizeBytes(long maxFileSizeBytes) {
            this.maxFileSizeBytes = maxFileSizeBytes;
        }

        public List<String> getAllowedContentTypes() {
            return allowedContentTypes;
        }

        public void setAllowedContentTypes(List<String> allowedContentTypes) {
            this.allowedContentTypes = allowedContentTypes;
        }
    }

    /**
     * Local development seed data. The administrator password has no default on
     * purpose - it is only used when the operator provides one explicitly.
     */
    public static class Seed {
        private boolean enabled = false;
        private String adminEmail;
        private String adminPassword;
        private String adminDisplayName = "FindIT Administrator";
        private String demoUserPassword;
        private boolean demoData = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getAdminEmail() {
            return adminEmail;
        }

        public void setAdminEmail(String adminEmail) {
            this.adminEmail = adminEmail;
        }

        public String getAdminPassword() {
            return adminPassword;
        }

        public void setAdminPassword(String adminPassword) {
            this.adminPassword = adminPassword;
        }

        public String getAdminDisplayName() {
            return adminDisplayName;
        }

        public void setAdminDisplayName(String adminDisplayName) {
            this.adminDisplayName = adminDisplayName;
        }

        public String getDemoUserPassword() {
            return demoUserPassword;
        }

        public void setDemoUserPassword(String demoUserPassword) {
            this.demoUserPassword = demoUserPassword;
        }

        public boolean isDemoData() {
            return demoData;
        }

        public void setDemoData(boolean demoData) {
            this.demoData = demoData;
        }
    }

    /**
     * Cross origin configuration - only needed when the front-end is hosted on a
     * different origin than the API.
     */
    public static class Cors {
        private List<String> allowedOrigins = new ArrayList<>();

        public List<String> getAllowedOrigins() {
            return allowedOrigins;
        }

        public void setAllowedOrigins(List<String> allowedOrigins) {
            this.allowedOrigins = allowedOrigins;
        }
    }

    /**
     * Basic abuse protection.
     */
    public static class Security {
        /** Allowed login attempts per client and time window. */
        private int maxLoginAttempts = 10;
        /** Length of the rate limit window in seconds. */
        private int rateLimitWindowSeconds = 900;
        /** Allowed claim submissions per user and hour. */
        private int maxClaimsPerHour = 20;

        public int getMaxLoginAttempts() {
            return maxLoginAttempts;
        }

        public void setMaxLoginAttempts(int maxLoginAttempts) {
            this.maxLoginAttempts = maxLoginAttempts;
        }

        public int getRateLimitWindowSeconds() {
            return rateLimitWindowSeconds;
        }

        public void setRateLimitWindowSeconds(int rateLimitWindowSeconds) {
            this.rateLimitWindowSeconds = rateLimitWindowSeconds;
        }

        public int getMaxClaimsPerHour() {
            return maxClaimsPerHour;
        }

        public void setMaxClaimsPerHour(int maxClaimsPerHour) {
            this.maxClaimsPerHour = maxClaimsPerHour;
        }
    }
}
