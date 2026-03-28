package com.fern.reportservice.config;

public class ReportExportProperties {
    private String baseDir = System.getProperty("java.io.tmpdir") + "/fern-report-exports";
    private int previewRowLimit = 50;
    private int artifactRetentionDays = 30;

    public String getBaseDir() {
        return baseDir;
    }

    public void setBaseDir(String baseDir) {
        this.baseDir = baseDir;
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
}
