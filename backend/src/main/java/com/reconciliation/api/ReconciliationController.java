package com.reconciliation.api;

import com.reconciliation.api.dto.BreakItemDto;
import com.reconciliation.api.dto.LedgerSourceRowDto;
import com.reconciliation.api.dto.MerchantRollupDto;
import com.reconciliation.api.dto.QuarantineDto;
import com.reconciliation.api.dto.RunListItemDto;
import com.reconciliation.api.dto.RunSummaryDto;
import com.reconciliation.api.dto.SettlementSourceRowDto;
import com.reconciliation.engine.Classification;
import com.reconciliation.persistence.ImportRunEntity;
import com.reconciliation.service.ImportService;
import com.reconciliation.service.ReportService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class ReconciliationController {

    private final ImportService importService;
    private final ReportService reportService;

    public ReconciliationController(ImportService importService, ReportService reportService) {
        this.importService = importService;
        this.reportService = reportService;
    }

    @PostMapping("/imports")
    public ResponseEntity<Map<String, Object>> importFiles(
            @RequestParam("internal") MultipartFile internalFile,
            @RequestParam("settlement") MultipartFile settlementFile) throws IOException {

        if (internalFile.isEmpty() || settlementFile.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Both files are required and must be non-empty"));
        }

        ImportService.ImportOutcome outcome = importService.importFiles(
                internalFile.getOriginalFilename(),
                new String(internalFile.getBytes(), StandardCharsets.UTF_8),
                settlementFile.getOriginalFilename(),
                new String(settlementFile.getBytes(), StandardCharsets.UTF_8));

        return ResponseEntity
                .status(outcome.alreadyImported() ? HttpStatus.OK : HttpStatus.CREATED)
                .body(Map.of(
                        "runId", outcome.run().getId(),
                        "alreadyImported", outcome.alreadyImported()));
    }

    @GetMapping("/runs")
    public List<RunListItemDto> runs() {
        return reportService.allRuns().stream()
                .map(run -> new RunListItemDto(
                        run.getId(), run.getImportedAt(),
                        run.getInternalFileName(), run.getSettlementFileName(),
                        run.getValidInternalCount(), run.getValidSettlementCount(),
                        run.getQuarantinedCount()))
                .toList();
    }

    @GetMapping("/runs/latest/summary")
    public ResponseEntity<RunSummaryDto> latestSummary() {
        return reportService.latestRun()
                .map(run -> ResponseEntity.ok(reportService.summary(run)))
                .orElse(ResponseEntity.noContent().build());
    }

    @GetMapping("/runs/{runId}/summary")
    public RunSummaryDto summary(@PathVariable Long runId) {
        ImportRunEntity run = reportService.requireRun(runId);
        return reportService.summary(run);
    }

    @GetMapping("/runs/{runId}/breaks")
    public List<BreakItemDto> breaks(
            @PathVariable Long runId,
            @RequestParam(name = "category", required = false) Classification category) {
        reportService.requireRun(runId);
        return reportService.breaks(runId, category);
    }

    @GetMapping("/runs/{runId}/merchants")
    public List<MerchantRollupDto> merchants(@PathVariable Long runId) {
        reportService.requireRun(runId);
        return reportService.merchantRollup(runId);
    }

    @GetMapping("/runs/{runId}/quarantine")
    public List<QuarantineDto> quarantine(@PathVariable Long runId) {
        reportService.requireRun(runId);
        return reportService.quarantine(runId);
    }

    @GetMapping("/runs/{runId}/source/ledger")
    public List<LedgerSourceRowDto> ledgerSource(@PathVariable Long runId) {
        reportService.requireRun(runId);
        return reportService.ledgerSourceRows(runId);
    }

    @GetMapping("/runs/{runId}/source/settlements")
    public List<SettlementSourceRowDto> settlementSource(@PathVariable Long runId) {
        reportService.requireRun(runId);
        return reportService.settlementSourceRows(runId);
    }

    @ExceptionHandler(ReportService.RunNotFoundException.class)
    public ResponseEntity<Map<String, Object>> runNotFound(ReportService.RunNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> badInput(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    }
}
