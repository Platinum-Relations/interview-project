package com.ek.reconciliation.api;

import com.ek.reconciliation.importing.InternalTransactionImportService;
import com.ek.reconciliation.importing.SettlementRecordImportService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ImportController {

    private final InternalTransactionImportService internalTransactionImportService;
    private final SettlementRecordImportService settlementRecordImportService;

    public ImportController(
            InternalTransactionImportService internalTransactionImportService,
            SettlementRecordImportService settlementRecordImportService
    ) {
        this.internalTransactionImportService = internalTransactionImportService;
        this.settlementRecordImportService = settlementRecordImportService;
    }

    @PostMapping("/api/import/internal-transactions/populate")
    public ImportResponse populateInternalTransactions() {
        return internalTransactionImportService.populateFromDefaultCsv();
    }

    @PostMapping("/api/import/processor-settlement/populate")
    public SettlementImportResponse populateSettlementRecords() {
        return settlementRecordImportService.populateFromDefaultJson();
    }
}

