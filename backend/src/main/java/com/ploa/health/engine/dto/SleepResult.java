package com.ploa.health.engine.dto;

public record SleepResult(
    String duration,
    String deepSleep,
    String lightSleep,
    String remSleep,
    String weeklyTrend
) {
}
