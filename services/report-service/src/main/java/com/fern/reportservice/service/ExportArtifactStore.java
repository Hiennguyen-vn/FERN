package com.fern.reportservice.service;

import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
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
            try (BufferedWriter writer = new BufferedWriter(
                    new OutputStreamWriter(new FileOutputStream(tempPath.toFile()), StandardCharsets.UTF_8)
            )) {
                writer.write(String.join(",", columns));
                writer.newLine();
                for (Map<String, Object> row : rows) {
                    for (int index = 0; index < columns.size(); index++) {
                        if (index > 0) {
                            writer.write(',');
                        }
                        writer.write(csvValue(row.get(columns.get(index))));
                    }
                    writer.newLine();
                }
            }
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
        String sanitized = startsWithSpreadsheetFormulaPrefix(text) ? "'" + text : text;
        String escaped = sanitized.replace("\"", "\"\"");
        if (escaped.contains(",") || escaped.contains("\"") || escaped.contains("\n")) {
            return "\"" + escaped + "\"";
        }
        return escaped;
    }

    private boolean startsWithSpreadsheetFormulaPrefix(String text) {
        return !text.isEmpty()
                && (text.charAt(0) == '=' || text.charAt(0) == '+' || text.charAt(0) == '-' || text.charAt(0) == '@');
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
