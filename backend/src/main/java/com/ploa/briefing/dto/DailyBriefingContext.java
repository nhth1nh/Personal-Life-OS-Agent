package com.ploa.briefing.dto;

import com.ploa.finance.engine.dto.BurnRateResult;
import com.ploa.finance.engine.dto.CashFlowSummary;
import com.ploa.health.engine.dto.ReadinessResult;
import com.ploa.health.engine.dto.WeightEmaResult;
import java.time.LocalDate;

public record DailyBriefingContext(
        LocalDate date,
        ReadinessResult readiness,
        WeightEmaResult weightEma,
        BurnRateResult burnRate,
        CashFlowSummary cashFlow
) {
    public DailyBriefingContext(LocalDate date, ReadinessResult readiness, WeightEmaResult weightEma, BurnRateResult burnRate) {
        this(date, readiness, weightEma, burnRate, null);
    }
}
