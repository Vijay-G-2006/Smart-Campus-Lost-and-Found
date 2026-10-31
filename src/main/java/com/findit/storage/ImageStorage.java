package com.findit.storage;

import org.springframework.core.io.Resource;

import java.io.IOException;
import java.io.InputStream;

/**
 * Abstraction for the (private) image storage.
 *
 * <p>The application never depends on Azure directly - the local development
 * implementation writes to a directory outside the static resources, the Azure
 * implementation writes to a private blob container.</p>
 */
public interface ImageStorage {

    /**
     * Stores an image and returns the generated (opaque) storage name.
     *
     * @param originalFileName the client supplied file name - it is never used
     *                         as a storage path, only its extension is inspected
     * @param content          the image content
     * @param size             the content length in bytes
     * @param contentType      the validated content type
     */
    String store(String originalFileName, InputStream content, long size, String contentType) throws IOException;

    /**
     * Loads a stored image.
     */
    Resource load(String storageName) throws IOException;

    /**
     * Deletes a stored image (no-op when it does not exist).
     */
    void delete(String storageName);

    /**
     * Short description of the active implementation (for the health endpoint).
     */
    String describe();
}
