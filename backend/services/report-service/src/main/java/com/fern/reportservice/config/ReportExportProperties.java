package com.fern.reportservice.config;

public class ReportExportProperties {
    public enum StorageBackend {
        FILESYSTEM,
        S3
    }

    private StorageBackend storageBackend = StorageBackend.FILESYSTEM;
    private String baseDir = System.getProperty("java.io.tmpdir") + "/fern-report-exports";
    private String tempDir = System.getProperty("java.io.tmpdir") + "/fern-report-export-staging";
    private String bucket;
    private String keyPrefix = "exports";
    private String region = "us-east-1";
    private String endpoint;
    private String accessKey;
    private String secretKey;
    private boolean pathStyleAccessEnabled;
    private int previewRowLimit = 50;
    private int artifactRetentionDays = 30;

    public StorageBackend getStorageBackend() {
        return storageBackend;
    }

    public void setStorageBackend(StorageBackend storageBackend) {
        this.storageBackend = storageBackend == null ? StorageBackend.FILESYSTEM : storageBackend;
    }

    public String getBaseDir() {
        return baseDir;
    }

    public void setBaseDir(String baseDir) {
        this.baseDir = baseDir;
    }

    public String getTempDir() {
        return tempDir;
    }

    public void setTempDir(String tempDir) {
        this.tempDir = tempDir;
    }

    public String getBucket() {
        return bucket;
    }

    public void setBucket(String bucket) {
        this.bucket = bucket;
    }

    public String getKeyPrefix() {
        return keyPrefix;
    }

    public void setKeyPrefix(String keyPrefix) {
        this.keyPrefix = keyPrefix;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public String getAccessKey() {
        return accessKey;
    }

    public void setAccessKey(String accessKey) {
        this.accessKey = accessKey;
    }

    public String getSecretKey() {
        return secretKey;
    }

    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }

    public boolean isPathStyleAccessEnabled() {
        return pathStyleAccessEnabled;
    }

    public void setPathStyleAccessEnabled(boolean pathStyleAccessEnabled) {
        this.pathStyleAccessEnabled = pathStyleAccessEnabled;
    }

    public int getPreviewRowLimit() {
        return previewRowLimit;
    }

    public void setPreviewRowLimit(int previewRowLimit) {
        this.previewRowLimit = previewRowLimit;
    }

    public int getArtifactRetentionDays() {
        return artifactRetentionDays;
    }

    public void setArtifactRetentionDays(int artifactRetentionDays) {
        this.artifactRetentionDays = artifactRetentionDays;
    }

    public boolean usesS3() {
        return storageBackend == StorageBackend.S3;
    }

    public String storageLocation() {
        return usesS3() ? keyPrefix : baseDir;
    }
}
