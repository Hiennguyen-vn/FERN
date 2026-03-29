package com.fern.reportservice.service;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Map;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

@Component
public class ExportArtifactStore {
    private final ExportArtifactStorage storage;

    public ExportArtifactStore(ExportArtifactStorage storage) {
        this.storage = storage;
    }

    public ReportArtifact writeCsv(String baseLocation, Long jobId, String datasetName, List<String> columns, List<Map<String, Object>> rows) {
        try (CsvArtifactWriter writer = openCsv(baseLocation, jobId, datasetName, columns)) {
            for (Map<String, Object> row : rows) {
                writer.writeRow(row);
            }
            return writer.finish();
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to generate export artifact", exception);
        }
    }

    public CsvArtifactWriter openCsv(String baseLocation, Long jobId, String datasetName, List<String> columns) {
        try {
            return storage.openCsv(baseLocation, jobId, datasetName, columns);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to generate export artifact", exception);
        }
    }

    public void deleteQuietly(String filePath) {
        storage.deleteQuietly(filePath);
    }

    public Resource resolve(String filePath) {
        return storage.resolve(filePath);
    }

    public String fileName(String filePath) {
        return storage.fileName(filePath);
    }

    static String csvValue(Object value) {
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

    private static boolean startsWithSpreadsheetFormulaPrefix(String text) {
        return !text.isEmpty()
                && (text.charAt(0) == '=' || text.charAt(0) == '+' || text.charAt(0) == '-' || text.charAt(0) == '@');
    }

    static String sha256(Path filePath) {
        try {
            byte[] content = java.nio.file.Files.readAllBytes(filePath);
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

    public record ReportArtifact(String path, String checksum) {
    }

    public interface CsvArtifactWriter extends AutoCloseable {
        void writeRow(Map<String, Object> row) throws IOException;

        ReportArtifact finish() throws IOException;

        @Override
        void close() throws IOException;
    }
}
