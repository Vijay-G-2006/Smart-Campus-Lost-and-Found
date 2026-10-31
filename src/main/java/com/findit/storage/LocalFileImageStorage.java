package com.findit.storage;

import com.findit.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.Locale;
import java.util.UUID;

/**
 * Local development image storage.
 *
 * <p>Images are written outside of the static resource paths
 * ({@code local-data/images} by default) and are only served through the
 * authorised backend endpoint. Uploaded files are never executable content
 * because only a fixed set of image types is accepted and the extension is
 * derived from the validated content type.</p>
 */
@Component
@ConditionalOnProperty(name = "app.storage.type", havingValue = "local", matchIfMissing = true)
public class LocalFileImageStorage implements ImageStorage {

    private static final Logger log = LoggerFactory.getLogger(LocalFileImageStorage.class);

    private final Path root;

    public LocalFileImageStorage(AppProperties appProperties) {
        this.root = Paths.get(appProperties.getStorage().getLocalDir()).toAbsolutePath().normalize();
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            log.warn("Could not create local image directory {}", root);
        }
        log.info("Using local image storage at {}", root);
    }

    @Override
    public String store(String originalFileName, InputStream content, long size, String contentType) throws IOException {
        LocalDate today = LocalDate.now();
        String extension = extensionFor(contentType);
        String storageName = today.getYear() + "/" + String.format(Locale.ROOT, "%02d", today.getMonthValue())
                + "/" + UUID.randomUUID() + extension;

        Path target = root.resolve(storageName).normalize();
        if (!target.startsWith(root)) {
            throw new IOException("Invalid storage path");
        }
        Files.createDirectories(target.getParent());
        Files.copy(content, target, StandardCopyOption.REPLACE_EXISTING);
        return storageName;
    }

    @Override
    public Resource load(String storageName) throws IOException {
        Path path = root.resolve(storageName).normalize();
        if (!path.startsWith(root) || !Files.exists(path)) {
            throw new IOException("Image not found");
        }
        return new FileSystemResource(path);
    }

    @Override
    public void delete(String storageName) {
        try {
            Files.deleteIfExists(root.resolve(storageName).normalize());
        } catch (IOException e) {
            log.warn("Could not delete image {}", storageName);
        }
    }

    @Override
    public String describe() {
        return "local:" + root;
    }

    private String extensionFor(String contentType) {
        return switch (contentType == null ? "" : contentType.toLowerCase(Locale.ROOT)) {
            case "image/jpeg", "image/jpg" -> ".jpg";
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            case "image/gif" -> ".gif";
            default -> ".bin";
        };
    }
}
