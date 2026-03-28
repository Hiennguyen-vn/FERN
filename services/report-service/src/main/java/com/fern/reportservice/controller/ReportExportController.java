package com.fern.reportservice.controller;

import com.fern.platform.common.FernPrincipal;
import com.fern.reportservice.dto.ReportCommands.CreateExportRequest;
import com.fern.reportservice.dto.ReportResponses.ExportJobResponse;
import com.fern.reportservice.dto.ReportResponses.ExportPreviewResponse;
import com.fern.reportservice.service.ReportService;
import jakarta.validation.Valid;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/reports/exports")
public class ReportExportController {
    private final ReportService reportService;

    public ReportExportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @PostMapping
    public ExportJobResponse createExport(
            @AuthenticationPrincipal FernPrincipal principal,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody CreateExportRequest request
    ) {
        return reportService.createExport(principal, request, idempotencyKey);
    }

    @GetMapping("/{jobId}")
    public ExportJobResponse getExport(
            @AuthenticationPrincipal FernPrincipal principal,
            @PathVariable Long jobId
    ) {
        return reportService.getExport(principal, jobId);
    }

    @GetMapping("/{jobId}/preview")
    public ExportPreviewResponse previewExport(
            @AuthenticationPrincipal FernPrincipal principal,
            @PathVariable Long jobId
    ) {
        return reportService.previewExport(principal, jobId);
    }

    @GetMapping("/{jobId}/download")
    public ResponseEntity<Resource> downloadExport(
            @AuthenticationPrincipal FernPrincipal principal,
            @PathVariable Long jobId
    ) {
        ReportService.ExportDownload download = reportService.downloadExport(principal, jobId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(download.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + download.fileName() + "\"")
                .body(download.resource());
    }
}
