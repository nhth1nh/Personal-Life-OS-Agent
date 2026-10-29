package com.ploa.finance.api;

import com.ploa.finance.api.dto.CreateIncomeRequest;
import com.ploa.finance.model.Income;
import com.ploa.finance.model.IncomeStatus;
import com.ploa.finance.service.IncomeService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/finance/incomes")
public class IncomeController {

    private final IncomeService incomeService;

    public IncomeController(IncomeService incomeService) {
        this.incomeService = incomeService;
    }

    @GetMapping
    public ResponseEntity<List<Income>> getIncomes(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) IncomeStatus status
    ) {
        return ResponseEntity.ok(incomeService.getIncomes(from, to, status));
    }

    @PostMapping
    public ResponseEntity<Income> createIncome(@RequestBody CreateIncomeRequest req) {
        return ResponseEntity.ok(incomeService.createIncome(req));
    }

    public record ReceiveIncomeRequest(
            BigDecimal actualAmount,
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate receivedDate,
            UUID accountId
    ) {}

    @PostMapping("/{id}/receive")
    public ResponseEntity<Income> markAsReceived(
            @PathVariable UUID id,
            @RequestBody ReceiveIncomeRequest req
    ) {
        return ResponseEntity.ok(incomeService.markAsReceived(
                id,
                req.actualAmount(),
                req.receivedDate(),
                req.accountId()
        ));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteIncome(@PathVariable UUID id) {
        incomeService.deleteIncome(id);
        return ResponseEntity.noContent().build();
    }
}
