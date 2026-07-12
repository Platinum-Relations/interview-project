package com.ek.reconciliation.api;

import com.ek.reconciliation.reconciling.FeeApplicationResponse;
import com.ek.reconciliation.reconciling.FeeApplicationService;
import com.ek.reconciliation.reconciling.ReconciliationResultResponse;
import com.ek.reconciliation.reconciling.ReconciliationService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ReconciliationController {

    private final FeeApplicationService feeApplicationService;
    private final ReconciliationService reconciliationService;

    public ReconciliationController(
            FeeApplicationService feeApplicationService,
            ReconciliationService reconciliationService
    ) {
        this.feeApplicationService = feeApplicationService;
        this.reconciliationService = reconciliationService;
    }

    @PostMapping("/api/reconciliation/apply-fees")
    public FeeApplicationResponse applyExpectedFees() {
        return feeApplicationService.applyExpectedFeesToMerchantRefMatches();
    }

    @PostMapping("/api/reconciliation/run")
    public ReconciliationResultResponse runReconciliation() {
        return reconciliationService.reconcileImportedData();
    }
}
