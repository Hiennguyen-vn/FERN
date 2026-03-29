package com.fern.reportservice.service;

import java.io.IOException;
import java.util.List;
import org.springframework.core.io.Resource;

public interface ExportArtifactStorage {
    ExportArtifactStore.CsvArtifactWriter openCsv(String baseLocation, Long jobId, String datasetName, List<String> columns) throws IOException;

    void deleteQuietly(String artifactPath);

    Resource resolve(String artifactPath);

    String fileName(String artifactPath);
}
