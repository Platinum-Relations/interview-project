package com.example.reconciliation;

import java.io.IOException;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1")
@CrossOrigin(origins = "${app.cors.allowed-origin:http://localhost:5173}")
public class ApiController {
    private final ReconciliationService service;
    public ApiController(ReconciliationService service) { this.service = service; }

    @GetMapping("/health") public Map<String, Object> health() { return Map.of("status", "UP", "service", "payment-reconciliation"); }
    @GetMapping("/dashboard") public Map<String, Object> dashboard() { return service.dashboard(); }
    @GetMapping("/imports") public Map<String, Object> imports() { return service.importStatus(); }
    @PostMapping(value="/imports/internal-transactions", consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> importInternal(@RequestParam("file") MultipartFile file) throws IOException { return service.importInternal(file); }
    @PostMapping(value="/imports/settlements", consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> importSettlements(@RequestParam("file") MultipartFile file) throws IOException { return service.importSettlements(file); }
    @PostMapping("/reconciliations") public Map<String, Object> reconcile() { return service.reconcile(); }
    @GetMapping("/reconciliations/latest") public Map<String, Object> latest() { return service.latestResults(); }
    @GetMapping("/reconciliations/{runId}") public Map<String, Object> run(@PathVariable String runId) { return service.runById(runId); }
    @GetMapping("/reconciliations/{runId}/breaks") public Map<String, Object> breaks(@PathVariable String runId, @RequestParam(required=false) String category, @RequestParam(required=false) String merchant, @RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="25") int size) {
        return service.breaks(runId, category, merchant, page, size);
    }
    @GetMapping("/reconciliations/{runId}/merchants") public Map<String, Object> merchants(@PathVariable String runId) { return service.merchants(runId); }
}
