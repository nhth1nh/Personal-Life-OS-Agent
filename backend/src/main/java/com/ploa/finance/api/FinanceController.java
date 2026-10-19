package com.ploa.finance.api;

import com.ploa.finance.engine.dto.BurnRateResult;
import com.ploa.finance.model.Transaction;
import com.ploa.finance.service.FinanceService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/finance")
public class FinanceController {

    private final FinanceService financeService;

    public FinanceController(FinanceService financeService) {
        this.financeService = financeService;
    }

    /**
     * Get month-to-date burn rate, remaining budget, and safe daily allowance.
     */
    @GetMapping("/burn-rate")
    public ResponseEntity<BurnRateResult> getBurnRate() {
        return ResponseEntity.ok(financeService.getBurnRate());
    }

    /**
     * List recent transactions for the current month.
     */
    @GetMapping("/transactions")
    public ResponseEntity<List<Transaction>> getRecentTransactions(
            @RequestParam(defaultValue = "30") int limit) {
        return ResponseEntity.ok(financeService.getRecentTransactions(limit));
    }
}
