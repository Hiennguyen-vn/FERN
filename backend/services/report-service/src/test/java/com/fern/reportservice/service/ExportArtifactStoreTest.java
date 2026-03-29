package com.fern.reportservice.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ExportArtifactStoreTest {
    private final ExportArtifactStore store = new ExportArtifactStore(new FileSystemExportArtifactStorage());

    @Test
    void shouldPrefixSpreadsheetFormulaCells(@TempDir Path tempDir) throws IOException {
        Path path = writeCsv(tempDir, orderedValues(
                "equals", "=1+1",
                "plus", "+1",
                "minus", "-1",
                "at", "@cmd"
        ));

        assertThat(Files.readString(path)).isEqualTo("""
                equals,plus,minus,at
                '=1+1,'+1,'-1,'@cmd
                """);
    }

    @Test
    void shouldSanitizeBeforeExistingCommaAndQuoteEscaping(@TempDir Path tempDir) throws IOException {
        Path path = writeCsv(tempDir, orderedValues(
                "formula", "=SUM(1,2)",
                "quoted", "@cmd\"test\""
        ));

        assertThat(Files.readString(path)).isEqualTo(
                "formula,quoted\n" +
                "\"'=SUM(1,2)\",\"'@cmd\"\"test\"\"\"\n"
        );
    }

    private Path writeCsv(Path tempDir, Map<String, Object> values) {
        Map<String, Object> row = new LinkedHashMap<>(values);
        ExportArtifactStore.ReportArtifact artifact = store.writeCsv(
                tempDir.toString(),
                42L,
                "dataset",
                List.copyOf(row.keySet()),
                List.of(row)
        );
        return Path.of(artifact.path());
    }

    private Map<String, Object> orderedValues(Object... values) {
        Map<String, Object> ordered = new LinkedHashMap<>();
        for (int index = 0; index < values.length; index += 2) {
            ordered.put((String) values[index], values[index + 1]);
        }
        return ordered;
    }
}
