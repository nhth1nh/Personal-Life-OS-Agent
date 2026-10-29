package com.ploa.finance.api;

import com.ploa.finance.api.dto.CreateDebtRequest;
import com.ploa.finance.api.dto.RecordDebtPaymentRequest;
import com.ploa.finance.engine.dto.PayoffSimulationComparison;
import com.ploa.finance.model.Debt;
import com.ploa.finance.model.DebtPayment;
import com.ploa.finance.model.DebtSchedule;
import com.ploa.finance.service.DebtService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/finance/debts")
public class DebtController {

    private final DebtService debtService;

    public DebtController(DebtService debtService) {
        this.debtService = debtService;
    }

    @GetMapping
    public ResponseEntity<List<Debt>> getDebts() {
        return ResponseEntity.ok(debtService.getActiveDebts());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Debt> getDebtById(@PathVariable UUID id) {
        return ResponseEntity.ok(debtService.getDebtById(id));
    }

    @PostMapping
    public ResponseEntity<Debt> createDebt(@RequestBody CreateDebtRequest req) {
        return ResponseEntity.ok(debtService.createDebt(req));
    }

    @GetMapping("/{id}/schedule")
    public ResponseEntity<List<DebtSchedule>> getDebtSchedule(@PathVariable UUID id) {
        return ResponseEntity.ok(debtService.getDebtSchedule(id));
    }

    @PostMapping("/{id}/payments")
    public ResponseEntity<DebtPayment> recordPayment(
            @PathVariable UUID id,
            @RequestBody RecordDebtPaymentRequest req
    ) {
        return ResponseEntity.ok(debtService.recordPayment(id, req));
    }

    @GetMapping("/simulate")
    public ResponseEntity<PayoffSimulationComparison> simulatePayoff(
            @RequestParam(required = false, defaultValue = "0") BigDecimal surplus
    ) {
        return ResponseEntity.ok(debtService.simulatePayoff(surplus));
    }
}
