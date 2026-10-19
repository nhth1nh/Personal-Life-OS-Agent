package com.ploa.assistant.tools;

import com.ploa.finance.engine.dto.BurnRateResult;
import com.ploa.finance.model.SpendType;
import com.ploa.finance.model.Transaction;
import com.ploa.finance.service.FinanceService;
import com.ploa.health.engine.dto.ReadinessResult;
import com.ploa.health.engine.dto.SleepResult;
import com.ploa.health.engine.dto.WeightEmaResult;
import com.ploa.health.service.HealthService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

/**
 * Spring AI Tool functions available to the Assistant LLM.
 * Allows the AI to query deterministic health and finance engines dynamically on demand.
 */
@Component
public class AssistantTools {

    private static final Logger log = LoggerFactory.getLogger(AssistantTools.class);

    private final HealthService healthService;
    private final FinanceService financeService;

    public AssistantTools(HealthService healthService, FinanceService financeService) {
        this.healthService = healthService;
        this.financeService = financeService;
    }

    public record TransactionSummary(
            LocalDate date,
            Long amount,
            String currency,
            String category,
            SpendType spendType,
            String merchant,
            String note,
            boolean confirmed
    ) {}

    @Tool(description = "Get physical recovery & readiness metrics for a date (or today). Includes HRV, Resting Heart Rate, rolling 30-day baseline Z-scores, recovery level (PRIME, NORMAL, STRESSED, FATIGUED), and sleep debt.")
    public ReadinessResult getHealthReadiness(
            @ToolParam(description = "Target date in YYYY-MM-DD format (leave null or blank for today)", required = false) String date) {
        LocalDate target = (date != null && !date.isBlank()) ? LocalDate.parse(date) : LocalDate.now();
        log.info("Assistant tool call: getHealthReadiness({})", target);
        return healthService.computeReadiness(target);
    }

    @Tool(description = "Get sleep stage breakdown (deep, light, REM, total duration) and 7-day weekly sleep duration trend.")
    public SleepResult getSleepBreakdown() {
        log.info("Assistant tool call: getSleepBreakdown()");
        return healthService.computeSleepTrend();
    }

    @Tool(description = "Get body weight trend, 7-day Exponential Moving Average (EMA) smoothed weight, delta to target goal, and weekly weight velocity.")
    public WeightEmaResult getWeightTrend() {
        log.info("Assistant tool call: getWeightTrend()");
        return healthService.computeWeightEma();
    }

    @Tool(description = "Get month-to-date personal finance status: monthly budget, total spend, discretionary vs fixed spend, remaining budget, daily burn rate, safe daily allowance, and savings rate.")
    public BurnRateResult getFinancialBurnRate() {
        log.info("Assistant tool call: getFinancialBurnRate()");
        return financeService.getBurnRate();
    }

    @Tool(description = "Get recent logged financial transactions for the current month.")
    public List<TransactionSummary> getRecentTransactions(
            @ToolParam(description = "Maximum number of transactions to retrieve (e.g. 5, 10, 20)", required = false) Integer limit) {
        int l = (limit != null && limit > 0) ? limit : 10;
        log.info("Assistant tool call: getRecentTransactions(limit={})", l);
        List<Transaction> txs = financeService.getRecentTransactions(l);
        return txs.stream()
                .map(t -> new TransactionSummary(
                        t.getTxDate(),
                        t.getAmount(),
                        t.getCurrency(),
                        t.getCategory(),
                        t.getSpendType(),
                        t.getMerchant(),
                        t.getNote(),
                        t.isConfirmed()
                ))
                .toList();
    }
}
