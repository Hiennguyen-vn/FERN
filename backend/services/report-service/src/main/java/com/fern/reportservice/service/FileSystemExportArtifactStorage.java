package com.fern.reportservice.service;

import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Locale;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;

public class FileSystemExportArtifactStorage implements ExportArtifactStorage {
    @Override
    public ExportArtifactStore.CsvArtifactWriter openCsv(String baseLocation, Long jobId, String datasetName, List<String> columns) throws IOException {
        return new FileSystemCsvArtifactWriter(Paths.get(baseLocation).toAbsolutePath().normalize(), jobId, datasetName, columns);
    }

    @Override
    public void deleteQuietly(String artifactPath) {
        if (artifactPath == null || artifactPath.isBlank()) {
            return;
        }
        try {
            Files.deleteIfExists(Paths.get(artifactPath));
        } catch (IOException ignored) {
        }
    }

    @Override
    public Resource resolve(String artifactPath) {
        Path path = Paths.get(artifactPath).toAbsolutePath().normalize();
        if (!Files.exists(path)) {
            return null;
        }
        return new FileSystemResource(path);
    }

    @Override
    public String fileName(String artifactPath) {
        return Paths.get(artifactPath).getFileName().toString();
    }

    private static final class FileSystemCsvArtifactWriter implements ExportArtifactStore.CsvArtifactWriter {
        private final List<String> columns;
        private final Path tempPath;
        private final Path finalPath;
        private final BufferedWriter writer;
        private boolean finished;

        private FileSystemCsvArtifactWriter(Path baseDir, Long jobId, String datasetName, List<String> columns) throws IOException {
            Files.createDirectories(baseDir);
            this.columns = List.copyOf(columns);
            this.tempPath = baseDir.resolve(jobId + "-" + datasetName.toLowerCase(Locale.ROOT) + ".csv.tmp");
            this.finalPath = baseDir.resolve(jobId + "-" + datasetName.toLowerCase(Locale.ROOT) + ".csv");
            this.writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(tempPath.toFile()), StandardCharsets.UTF_8));
            writer.write(String.join(",", this.columns));
            writer.newLine();
        }

        @Override
        public void writeRow(java.util.Map<String, Object> row) throws IOException {
            for (int index = 0; index < columns.size(); index++) {
                if (index > 0) {
                    writer.write(',');
                }
                writer.write(ExportArtifactStore.csvValue(row.get(columns.get(index))));
            }
            writer.newLine();
        }

        @Override
        public ExportArtifactStore.ReportArtifact finish() throws IOException {
            if (finished) {
                throw new IllegalStateException("CSV artifact is already finalized");
            }
            writer.flush();
            writer.close();
            String checksum = ExportArtifactStore.sha256(tempPath);
            Files.move(tempPath, finalPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            finished = true;
            return new ExportArtifactStore.ReportArtifact(finalPath.toString(), checksum);
        }

        @Override
        public void close() throws IOException {
            if (finished) {
                return;
            }
            try {
                writer.close();
            } finally {
                Files.deleteIfExists(tempPath);
            }
        }
    }
}
