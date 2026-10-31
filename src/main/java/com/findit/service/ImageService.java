package com.findit.service;

import com.findit.config.AppProperties;
import com.findit.exception.BadRequestException;
import com.findit.storage.ImageStorage;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;

/**
 * Validates uploaded images and delegates the actual storage to the
 * configured {@link ImageStorage} implementation.
 */
@Service
public class ImageService {

    private final ImageStorage imageStorage;
    private final AppProperties appProperties;

    public ImageService(ImageStorage imageStorage, AppProperties appProperties) {
        this.imageStorage = imageStorage;
        this.appProperties = appProperties;
    }

    /**
     * Validates the file type and size, then stores the image.
     */
    public StoredImage store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Please choose an image to upload");
        }
        long maxSize = appProperties.getStorage().getMaxFileSizeBytes();
        if (file.getSize() > maxSize) {
            throw new BadRequestException("The image must not be larger than "
                    + (maxSize / (1024 * 1024)) + " MB");
        }
        String contentType = file.getContentType() == null
                ? ""
                : file.getContentType().toLowerCase(Locale.ROOT).trim();
        if (!appProperties.getStorage().getAllowedContentTypes().contains(contentType)) {
            throw new BadRequestException("Only " + String.join(", ",
                    appProperties.getStorage().getAllowedContentTypes()) + " images are accepted");
        }
        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException e) {
            throw new BadRequestException("The image could not be read");
        }
        verifySignature(new ByteArrayInputStream(content), contentType);
        try (InputStream in = new ByteArrayInputStream(content)) {
            String storageName = imageStorage.store(file.getOriginalFilename(), in, content.length, contentType);
            return new StoredImage(storageName, contentType, content.length);
        } catch (IOException e) {
            throw new BadRequestException("The image could not be processed");
        }
    }

    public Resource load(String storageName) {
        try {
            return imageStorage.load(storageName);
        } catch (IOException e) {
            throw new BadRequestException("The image is not available");
        }
    }

    public void delete(String storageName) {
        if (storageName != null && !storageName.isBlank()) {
            imageStorage.delete(storageName);
        }
    }

    public String describeStorage() {
        return imageStorage.describe();
    }

    /**
     * Verifies that the file really is an image of the declared type. This
     * prevents executable content from being stored under an image name.
     */
    private void verifySignature(InputStream in, String contentType) {
        byte[] header = new byte[12];
        int read;
        try {
            read = in.read(header, 0, header.length);
        } catch (IOException e) {
            throw new BadRequestException("The uploaded file is not a valid image");
        }
        if (read < 4) {
            throw new BadRequestException("The uploaded file is not a valid image");
        }
        boolean matches = switch (contentType) {
            case "image/jpeg" -> (header[0] & 0xFF) == 0xFF && (header[1] & 0xFF) == 0xD8;
            case "image/png" -> (header[0] & 0xFF) == 0x89 && header[1] == 0x50 && header[2] == 0x4E && header[3] == 0x47;
            case "image/gif" -> header[0] == 'G' && header[1] == 'I' && header[2] == 'F';
            case "image/webp" -> read >= 12 && header[0] == 'R' && header[1] == 'I' && header[2] == 'F' && header[3] == 'F'
                    && header[8] == 'W' && header[9] == 'E' && header[10] == 'B' && header[11] == 'P';
            default -> false;
        };
        if (!matches) {
            throw new BadRequestException("The uploaded file does not look like a valid image");
        }
    }

    /**
     * The result of a successful upload.
     */
    public record StoredImage(String storageName, String contentType, long size) {
    }
}
