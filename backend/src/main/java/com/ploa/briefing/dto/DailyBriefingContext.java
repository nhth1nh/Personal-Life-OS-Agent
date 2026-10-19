package com.ploa.briefing.dto;

import com.ploa.finance.engine.dto.BurnRateResult;
import com.ploa.health.engine.dto.ReadinessResult;
import com.ploa.health.engine.dto.WeightEmaResult;
import java.time.LocalDate;

public record DailyBriefingContext(
        LocalDate date,
        ReadinessResult readiness,
        WeightEmaResult weightEma,
        BurnRateResult burnRate
) {}
