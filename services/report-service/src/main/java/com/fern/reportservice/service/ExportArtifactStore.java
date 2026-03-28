package com.fern.reportservice.service;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class ExportArtifactStore {
    public ReportArtifact writeCsv(Path baseDir, Long jobId, String datasetName, List<String> columns, List<Map<String, Object>> rows) {
        try {
            Files.createDirectories(baseDir);
            Path tempPath = baseDir.resolve(jobId + "-" + datasetName.toLowerCase(Locale.ROOT) + ".csv.tmp");
            Path finalPath = baseDir.resolve(jobId + "-" + datasetName.toLowerCase(Locale.ROOT) + ".csv");
            StringBuilder builder = new StringBuilder();
            builder.append(String.join(",", columns)).append('\n');
            for (Map<String, Object> row : rows) {
                for (int index = 0; index < columns.size(); index++) {
                    if (index > 0) {
                        builder.append(',');
                    }
                    builder.append(csvValue(row.get(columns.get(index))));
                }
                builder.append('\n');
            }
            Files.writeString(tempPath, builder.toString(), StandardCharsets.UTF_8);
            String checksum = sha256(tempPath);
            Files.move(tempPath, finalPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            return new ReportArtifact(finalPath, checksum);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to generate export artifact", exception);
        }
    }

    public void deleteQuietly(String filePath) {
        if (filePath == null || filePath.isBlank()) {
            return;
        }
        try {
            Files.deleteIfExists(Paths.get(filePath));
        } catch (IOException ignored) {
        }
    }

    private String csvValue(Object value) {
        if (value == null) {
            return "";
        }
        String text = value instanceof BigDecimal decimal ? decimal.stripTrailingZeros().toPlainString() : String.valueOf(value);
        String escaped = text.replace("\"", "\"\"");
        if (escaped.contains(",") || escaped.contains("\"") || escaped.contains("\n")) {
            return "\"" + escaped + "\"";
        }
        return escaped;
    }

    private String sha256(Path filePath) {
        try {
            byte[] content = Files.readAllBytes(filePath);
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(content);
            StringBuilder builder = new StringBuilder();
            for (byte item : digest) {
                builder.append(String.format("%02x", item));
            }
            return builder.toString();
        } catch (IOException | NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Unable to compute export checksum", exception);
        }
    }

    public record ReportArtifact(Path path, String checksum) {
    }
}
