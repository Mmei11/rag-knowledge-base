package com.example.ragkb.document.config;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.document")
public class DocumentStorageProperties {

    private String storageDir = "storage/documents";

    private long maxSizeBytes = 10 * 1024 * 1024;

    private List<String> allowedExtensions = new ArrayList<>(List.of("pdf", "txt", "md", "markdown"));

    private List<String> allowedContentTypes = new ArrayList<>(List.of(
            "application/pdf",
            "text/plain",
            "text/markdown",
            "text/x-markdown",
            "application/octet-stream"));

    public String getStorageDir() {
        return storageDir;
    }

    public void setStorageDir(String storageDir) {
        this.storageDir = storageDir;
    }

    public long getMaxSizeBytes() {
        return maxSizeBytes;
    }

    public void setMaxSizeBytes(long maxSizeBytes) {
        this.maxSizeBytes = maxSizeBytes;
    }

    public List<String> getAllowedExtensions() {
        return allowedExtensions;
    }

    public void setAllowedExtensions(List<String> allowedExtensions) {
        this.allowedExtensions = allowedExtensions;
    }

    public List<String> getAllowedContentTypes() {
        return allowedContentTypes;
    }

    public void setAllowedContentTypes(List<String> allowedContentTypes) {
        this.allowedContentTypes = allowedContentTypes;
    }
}
