package com.fern.reportservice.service;

import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.core.io.AbstractResource;
import org.springframework.core.io.Resource;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

public class S3ExportArtifactStorage implements ExportArtifactStorage {
    private final S3Client s3Client;
    private final String bucket;
    private final Path tempDir;

    public S3ExportArtifactStorage(S3Client s3Client, String bucket, String tempDir) {
        this.s3Client = s3Client;
        this.bucket = bucket;
        this.tempDir = Path.of(tempDir).toAbsolutePath().normalize();
    }

    @Override
    public ExportArtifactStore.CsvArtifactWriter openCsv(String keyPrefix, Long jobId, String datasetName, List<String> columns) throws IOException {
        return new S3CsvArtifactWriter(keyPrefix, jobId, datasetName, columns);
    }

    @Override
    public void deleteQuietly(String artifactPath) {
        ParsedLocation location = parseLocation(artifactPath);
        if (location == null) {
            return;
        }
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(location.bucket())
                    .key(location.key())
                    .build());
        } catch (RuntimeException ignored) {
        }
    }

    @Override
    public Resource resolve(String artifactPath) {
        ParsedLocation location = parseLocation(artifactPath);
        if (location == null) {
            return null;
        }
        try {
            long contentLength = s3Client.headObject(HeadObjectRequest.builder()
                    .bucket(location.bucket())
                    .key(location.key())
                    .build())
                    .contentLength();
            return new AbstractResource() {
                @Override
                public String getDescription() {
                    return artifactPath;
                }

                @Override
                public String getFilename() {
                    return fileName(artifactPath);
                }

                @Override
                public long contentLength() {
                    return contentLength;
                }

                @Override
                public InputStream getInputStream() {
                    return s3Client.getObject(GetObjectRequest.builder()
                            .bucket(location.bucket())
                            .key(location.key())
                            .build());
                }
            };
        } catch (NoSuchKeyException exception) {
            return null;
        } catch (S3Exception exception) {
            if (exception.statusCode() == 404) {
                return null;
            }
            throw exception;
        }
    }

    @Override
    public String fileName(String artifactPath) {
        ParsedLocation location = parseLocation(artifactPath);
        if (location == null) {
            return null;
        }
        int slash = location.key().lastIndexOf('/');
        return slash >= 0 ? location.key().substring(slash + 1) : location.key();
    }

    private ParsedLocation parseLocation(String artifactPath) {
        if (artifactPath == null || artifactPath.isBlank()) {
            return null;
        }
        URI uri = URI.create(artifactPath);
        if (!"s3".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getPath() == null || uri.getPath().length() <= 1) {
            return null;
        }
        return new ParsedLocation(uri.getHost(), uri.getPath().substring(1));
    }

    private final class S3CsvArtifactWriter implements ExportArtifactStore.CsvArtifactWriter {
        private final List<String> columns;
        private final String key;
        private final Path tempPath;
        private final BufferedWriter writer;
        private boolean finished;

        private S3CsvArtifactWriter(String keyPrefix, Long jobId, String datasetName, List<String> columns) throws IOException {
            Files.createDirectories(tempDir);
            this.columns = List.copyOf(columns);
            this.key = objectKey(keyPrefix, jobId, datasetName);
            this.tempPath = Files.createTempFile(tempDir, jobId + "-" + datasetName.toLowerCase(Locale.ROOT) + "-", ".csv.tmp");
            this.writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(tempPath.toFile()), StandardCharsets.UTF_8));
            writer.write(String.join(",", this.columns));
            writer.newLine();
        }

        @Override
        public void writeRow(Map<String, Object> row) throws IOException {
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
            s3Client.putObject(PutObjectRequest.builder()
                            .bucket(bucket)
                            .key(key)
                            .contentType("text/csv")
                            .build(),
                    RequestBody.fromFile(tempPath));
            finished = true;
            Files.deleteIfExists(tempPath);
            return new ExportArtifactStore.ReportArtifact("s3://" + bucket + "/" + key, checksum);
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

    private String objectKey(String keyPrefix, Long jobId, String datasetName) {
        String normalizedPrefix = keyPrefix == null ? "" : keyPrefix.trim();
        String suffix = jobId + "-" + datasetName.toLowerCase(Locale.ROOT) + ".csv";
        if (normalizedPrefix.isEmpty()) {
            return suffix;
        }
        return normalizedPrefix.endsWith("/") ? normalizedPrefix + suffix : normalizedPrefix + "/" + suffix;
    }

    private record ParsedLocation(String bucket, String key) {
    }
}
