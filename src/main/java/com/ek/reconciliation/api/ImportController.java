package com.ek.reconciliation.api;

import com.ek.reconciliation.importing.InternalTransactionImportService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ImportController {

    private final InternalTransactionImportService internalTransactionImportService;

    public ImportController(InternalTransactionImportService internalTransactionImportService) {
        this.internalTransactionImportService = internalTransactionImportService;
    }

    @PostMapping("/api/import/internal-transactions/populate")
    public ImportResponse populateInternalTransactions() {
        return internalTransactionImportService.populateFromDefaultCsv();
    }
}

