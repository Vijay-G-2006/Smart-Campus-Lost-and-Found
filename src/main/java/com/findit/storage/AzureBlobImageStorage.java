package com.findit.storage;

import com.azure.core.credential.TokenCredential;
import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.azure.storage.blob.models.BlobStorageException;
import com.findit.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.UUID;

/**
 * Azure Blob Storage implementation (production).
 *
 * <p>The container is always private - images are served through the
 * authorised backend endpoint, never through a public blob URL. Access is
 * granted either through a connection string from the environment or through
 * managed identity (preferred for Azure-to-Azure access).</p>
 */
@Component
@ConditionalOnProperty(name = "app.storage.type", havingValue = "azure")
public class AzureBlobImageStorage implements ImageStorage {

    private static final Logger log = LoggerFactory.getLogger(AzureBlobImageStorage.class);

    private final BlobContainerClient containerClient;

    public AzureBlobImageStorage(AppProperties appProperties) {
        String connectionString = appProperties.getStorage().getConnectionString();
        String accountName = appProperties.getStorage().getAccountName();
        BlobServiceClient serviceClient;

        if (StringUtils.hasText(connectionString)) {
            serviceClient = new BlobServiceClientBuilder().connectionString(connectionString).buildClient();
            log.info("Using Azure Blob Storage with a connection string");
        } else if (StringUtils.hasText(accountName)) {
            TokenCredential credential = new DefaultAzureCredentialBuilder().build();
            serviceClient = new BlobServiceClientBuilder()
                    .credential(credential)
                    .endpoint("https://" + accountName + ".blob.core.windows.net")
                    .buildClient();
            log.info("Using Azure Blob Storage with managed identity for account {}", accountName);
        } else {
            throw new IllegalStateException(
                    "app.storage.type=azure requires either app.storage.connection-string or app.storage.account-name");
        }

        this.containerClient = serviceClient.getBlobContainerClient(appProperties.getStorage().getContainer());
        verifyContainer();
        log.info("Azure Blob container ready (private): {}", appProperties.getStorage().getContainer());
    }

    /**
     * Makes sure the private container exists. A missing permission must not
     * stop the whole application - the failure is reported with the exact
     * remediation instead.
     */
    private void verifyContainer() {
        try {
            if (!containerClient.exists()) {
                containerClient.create();
                log.info("Created the private blob container");
            }
        } catch (BlobStorageException e) {
            log.error("The blob container '{}' could not be verified or created: {}", containerClient.getBlobContainerName(),
                    e.getErrorCode());
            log.error("Fix: grant the App Service identity the role 'Storage Blob Data Contributor' on the storage "
                    + "account, or create the container manually with "
                    + "'az storage container create --name {} --account-name <account> --public-access off'",
                    containerClient.getBlobContainerName());
        }
    }

    @Override
    public String store(String originalFileName, InputStream content, long size, String contentType) throws IOException {
        String storageName = UUID.randomUUID() + extensionFor(contentType);
        try {
            containerClient.getBlobClient(storageName).upload(content, size, true);
        } catch (BlobStorageException e) {
            throw new IOException("Could not store the image in Azure Blob Storage", e);
        }
        return storageName;
    }

    @Override
    public Resource load(String storageName) throws IOException {
        try {
            InputStream stream = containerClient.getBlobClient(storageName).openInputStream();
            return new InputStreamResource(stream);
        } catch (BlobStorageException e) {
            throw new IOException("Image not found", e);
        }
    }

    @Override
    public void delete(String storageName) {
        try {
            containerClient.getBlobClient(storageName).deleteIfExists();
        } catch (BlobStorageException e) {
            log.warn("Could not delete image {}", storageName);
        }
    }

    @Override
    public String describe() {
        return "azure-blob:" + containerClient.getBlobContainerName();
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
