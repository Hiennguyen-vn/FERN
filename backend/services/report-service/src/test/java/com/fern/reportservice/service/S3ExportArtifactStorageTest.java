package com.fern.reportservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.Resource;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.AbortableInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@ExtendWith(MockitoExtension.class)
class S3ExportArtifactStorageTest {
    @Mock
    private S3Client s3Client;

    @TempDir
    Path tempDir;

    @Test
    void shouldUploadCsvArtifactToObjectStorage() throws Exception {
        S3ExportArtifactStorage storage = new S3ExportArtifactStorage(s3Client, "fern-report-bucket", tempDir.toString());

        ExportArtifactStore.ReportArtifact artifact;
        try (ExportArtifactStore.CsvArtifactWriter writer = storage.openCsv("daily/exports", 42L, "EXPENSE_FACT", List.of("id", "name"))) {
            writer.writeRow(row("id", 1, "name", "coffee"));
            artifact = writer.finish();
        }

        ArgumentCaptor<PutObjectRequest> requestCaptor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client).putObject(requestCaptor.capture(), any(RequestBody.class));

        assertThat(requestCaptor.getValue().bucket()).isEqualTo("fern-report-bucket");
        assertThat(requestCaptor.getValue().key()).isEqualTo("daily/exports/42-expense_fact.csv");
        assertThat(artifact.path()).isEqualTo("s3://fern-report-bucket/daily/exports/42-expense_fact.csv");
        try (var files = Files.list(tempDir)) {
            assertThat(files).isEmpty();
        }
    }

    @Test
    void shouldResolveObjectStorageArtifactAsResource() throws Exception {
        S3ExportArtifactStorage storage = new S3ExportArtifactStorage(s3Client, "fern-report-bucket", tempDir.toString());
        byte[] payload = "id,name\n1,coffee\n".getBytes(StandardCharsets.UTF_8);
        when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenReturn(HeadObjectResponse.builder().contentLength((long) payload.length).build());
        when(s3Client.getObject(any(GetObjectRequest.class))).thenAnswer(invocation -> new ResponseInputStream<>(
                GetObjectResponse.builder().contentLength((long) payload.length).build(),
                AbortableInputStream.create(new ByteArrayInputStream(payload))
        ));

        Resource resource = storage.resolve("s3://fern-report-bucket/daily/exports/42-expense_fact.csv");

        assertThat(resource).isNotNull();
        assertThat(resource.getFilename()).isEqualTo("42-expense_fact.csv");
        assertThat(resource.contentLength()).isEqualTo(payload.length);
        assertThat(new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("id,name\n1,coffee\n");
        assertThat(new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("id,name\n1,coffee\n");
    }

    @Test
    void shouldDeleteObjectStorageArtifactQuietly() {
        S3ExportArtifactStorage storage = new S3ExportArtifactStorage(s3Client, "fern-report-bucket", tempDir.toString());

        storage.deleteQuietly("s3://fern-report-bucket/daily/exports/42-expense_fact.csv");

        ArgumentCaptor<DeleteObjectRequest> requestCaptor = ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(s3Client).deleteObject(requestCaptor.capture());
        assertThat(requestCaptor.getValue().bucket()).isEqualTo("fern-report-bucket");
        assertThat(requestCaptor.getValue().key()).isEqualTo("daily/exports/42-expense_fact.csv");
    }

    private Map<String, Object> row(Object... values) {
        Map<String, Object> row = new LinkedHashMap<>();
        for (int index = 0; index < values.length; index += 2) {
            row.put((String) values[index], values[index + 1]);
        }
        return row;
    }
}
