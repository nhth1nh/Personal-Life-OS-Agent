package com.ploa.finance.engine;

import com.ploa.finance.engine.dto.*;
import com.ploa.finance.model.Debt;
import com.ploa.finance.model.InterestAccrualConvention;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneId;
import java.util.*;

/**
 * Deterministic engine for loan amortization and debt payoff acceleration simulations.
 * Pure mathematical functions: never mutates input entities.
 * Floating-point numbers are forbidden in monetary math (strictly BigDecimal).
 */
@Component
public class DebtAmortizationEngine {

    public static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final int MAX_SIMULATION_MONTHS = 360; // 30-year ceiling

    /**
     * Calculates the contractual monthly annuity payment for an amortizing loan.
     * Formula: M = L * [r(1+r)^n] / [(1+r)^n - 1]
     */
    public BigDecimal calculateAnnuityMonthlyPayment(BigDecimal principal, BigDecimal apr, int totalMonths) {
        if (principal == null || principal.compareTo(BigDecimal.ZERO) <= 0 || totalMonths <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }

        BigDecimal effectiveApr = normalizeApr(apr);
        if (effectiveApr.compareTo(BigDecimal.ZERO) == 0) {
            return principal.divide(BigDecimal.valueOf(totalMonths), 2, RoundingMode.HALF_UP);
        }

        BigDecimal monthlyRate = effectiveApr.divide(BigDecimal.valueOf(12), 10, RoundingMode.HALF_UP);
        double r = monthlyRate.doubleValue();
        double factor = Math.pow(1.0 + r, totalMonths);

        if (factor == 1.0) {
            return principal.divide(BigDecimal.valueOf(totalMonths), 2, RoundingMode.HALF_UP);
        }

        double payment = principal.doubleValue() * (r * factor) / (factor - 1.0);
        return BigDecimal.valueOf(payment).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Numerically solves for the true effective APR using Internal Rate of Return (IRR) via Newton-Raphson.
     * Given Principal (L), Monthly Payment (P), and Number of Months (n):
     * Solves: (P/r) * [1 - (1+r)^(-n)] - L = 0
     * Returns true effective APR in percent (e.g. 17.88 for 17.88%).
     */
    public BigDecimal calculateEffectiveApr(BigDecimal principal, BigDecimal monthlyPayment, int totalMonths) {
        if (principal == null || monthlyPayment == null || totalMonths <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }

        double L = principal.doubleValue();
        double P = monthlyPayment.doubleValue();
        int n = totalMonths;

        if (L <= 0 || P <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }

        // Zero-interest check (e.g. 0% BNPL / installment)
        if (P * n <= L + 1.0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }

        // Initial guess based on average balance approximation
        double r = 2.0 * (P * n - L) / (L * (n + 1));
        if (r <= 0.0) r = 0.01;

        for (int i = 0; i < 60; i++) {
            double term = Math.pow(1.0 + r, -n);
            double g = (P / r) * (1.0 - term) - L;
            double gPrime = -P / (r * r) * (1.0 - term) + (P * n / r) * Math.pow(1.0 + r, -n - 1);

            if (Math.abs(gPrime) < 1e-12) {
                break;
            }

            double diff = g / gPrime;
            r = r - diff;

            if (r <= 0.0) {
                r = 1e-6;
            }

            if (Math.abs(diff) < 1e-8) {
                break;
            }
        }

        double aprPercent = r * 12.0 * 100.0;
        return BigDecimal.valueOf(aprPercent).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Calculates the stated Flat Interest Rate APR (Lãi suất phẳng / năm).
     * Formula: Flat APR = [(Total Payments - Principal) / Principal] * (12 / n) * 100%
     */
    public BigDecimal calculateFlatRateApr(BigDecimal principal, BigDecimal monthlyPayment, int totalMonths) {
        if (principal == null || monthlyPayment == null || totalMonths <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }

        double L = principal.doubleValue();
        double P = monthlyPayment.doubleValue();
        int n = totalMonths;

        if (L <= 0 || P * n <= L) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }

        double flatAprPercent = ((P * n - L) / L) * (12.0 / n) * 100.0;
        return BigDecimal.valueOf(flatAprPercent).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Analyzes loan terms, resolving missing APR, verifying stated rates, or exposing flat rate traps.
     */
    public AprAnalysisResult analyzeApr(BigDecimal principal, BigDecimal monthlyPayment, int totalMonths, BigDecimal statedApr) {
        if (principal == null || totalMonths <= 0) {
            return new AprAnalysisResult(BigDecimal.ZERO, BigDecimal.ZERO, true, "INSUFFICIENT_DATA", "Insufficient loan terms to calculate APR");
        }

        double L = principal.doubleValue();
        double P = (monthlyPayment != null) ? monthlyPayment.doubleValue() : 0.0;
        int n = totalMonths;

        // If monthly payment is missing or 0
        if (P <= 0.0) {
            if (statedApr != null && statedApr.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal normalizedStated = statedApr.compareTo(BigDecimal.ONE) > 0
                        ? statedApr.setScale(2, RoundingMode.HALF_UP)
                        : statedApr.multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP);
                return new AprAnalysisResult(
                        normalizedStated,
                        normalizedStated,
                        false,
                        "STATED_RATE",
                        String.format("Stated APR of %s%% (Monthly installment auto-calculated from loan terms)", normalizedStated)
                );
            }
            return new AprAnalysisResult(BigDecimal.ZERO, BigDecimal.ZERO, true, "INSUFFICIENT_DATA", "Insufficient loan terms to calculate APR");
        }

        // True 0% BNPL / installment check (must have P > 0, total payment <= L + 1, and stated APR must not be positive)
        boolean hasPositiveStatedRate = statedApr != null && statedApr.compareTo(BigDecimal.valueOf(0.01)) > 0;
        if (!hasPositiveStatedRate && (P * n <= L + 1.0)) {
            return new AprAnalysisResult(
                    BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                    BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                    true,
                    "ZERO_BNPL",
                    "0% interest rate (Total payments equal principal borrowed)"
            );
        }

        BigDecimal derivedEffectiveApr = calculateEffectiveApr(principal, monthlyPayment, totalMonths);
        BigDecimal flatApr = calculateFlatRateApr(principal, monthlyPayment, totalMonths);

        if (statedApr != null && statedApr.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal normalizedStated = statedApr.compareTo(BigDecimal.ONE) > 0
                    ? statedApr.setScale(2, RoundingMode.HALF_UP)
                    : statedApr.multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP);

            if (normalizedStated.subtract(derivedEffectiveApr).abs().compareTo(BigDecimal.valueOf(0.5)) <= 0) {
                return new AprAnalysisResult(
                        normalizedStated,
                        flatApr,
                        false,
                        "EXPLICIT_VERIFIED",
                        String.format("Verified declining-balance APR of %s%% matches annuity payment", normalizedStated)
                );
            } else if (normalizedStated.subtract(flatApr).abs().compareTo(BigDecimal.valueOf(0.5)) <= 0) {
                return new AprAnalysisResult(
                        derivedEffectiveApr,
                        flatApr,
                        false,
                        "FLAT_RATE_EXPOSED",
                        String.format("Stated rate of %s%% is FLAT interest; true effective declining APR is %s%%", normalizedStated, derivedEffectiveApr)
                );
            }
        }

        return new AprAnalysisResult(
                derivedEffectiveApr,
                flatApr,
                false,
                "DERIVED_IRR",
                String.format("Derived true effective APR of %s%% from %d monthly payments of %s VND (Stated flat rate: %s%%)",
                        derivedEffectiveApr, n, monthlyPayment, flatApr)
        );
    }

    /**
     * Generates the deterministic contractual amortization schedule for a given debt.
     */
    public DebtAmortizationResult generateSchedule(Debt debt, LocalDate asOfDate) {
        if (debt == null) {
            throw new IllegalArgumentException("Debt must not be null");
        }

        LocalDate startDate = asOfDate != null ? asOfDate : LocalDate.now(ZONE);
        BigDecimal principal = debt.getCurrentPrincipal() != null ? debt.getCurrentPrincipal() : debt.getOriginalPrincipal();
        if (principal == null || principal.compareTo(BigDecimal.ZERO) <= 0) {
            return new DebtAmortizationResult(
                    debt.getId(), debt.getName(), BigDecimal.ZERO, BigDecimal.ZERO,
                    BigDecimal.ZERO, startDate, Collections.emptyList()
            );
        }

        BigDecimal effectiveApr = normalizeApr(debt.getInterestRateApr());
        InterestAccrualConvention convention = debt.getInterestAccrualConvention() != null
                ? debt.getInterestAccrualConvention()
                : InterestAccrualConvention.ACTUAL_365;

        int termMonths = determineTermMonths(debt, startDate);
        BigDecimal targetPayment = debt.getMonthlyMinimumDue();
        if (targetPayment == null || targetPayment.compareTo(BigDecimal.ZERO) <= 0) {
            targetPayment = calculateAnnuityMonthlyPayment(principal, effectiveApr, termMonths);
        }

        List<AmortizationScheduleItem> items = new ArrayList<>();
        BigDecimal remaining = principal.setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalInterest = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        LocalDate currentDate = startDate;

        int installment = 1;
        while (remaining.compareTo(BigDecimal.ZERO) > 0 && installment <= MAX_SIMULATION_MONTHS) {
            currentDate = currentDate.plusMonths(1);
            int dueDay = debt.getDueDayOfMonth() > 0 ? Math.min(debt.getDueDayOfMonth(), currentDate.lengthOfMonth()) : currentDate.getDayOfMonth();
            LocalDate dueDate = currentDate.withDayOfMonth(dueDay);

            BigDecimal periodicRate = computePeriodicRate(effectiveApr, convention, dueDate);
            BigDecimal interest = remaining.multiply(periodicRate).setScale(2, RoundingMode.HALF_UP);
            totalInterest = totalInterest.add(interest);

            BigDecimal payment;
            BigDecimal principalPaid;

            if (remaining.add(interest).compareTo(targetPayment) <= 0) {
                // Final payoff installment
                payment = remaining.add(interest);
                principalPaid = remaining;
                remaining = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
            } else {
                payment = targetPayment;
                principalPaid = payment.subtract(interest);
                if (principalPaid.compareTo(BigDecimal.ZERO) <= 0) {
                    // Payment cannot cover interest; cap to prevent infinite loop
                    principalPaid = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
                    payment = interest;
                } else {
                    remaining = remaining.subtract(principalPaid).setScale(2, RoundingMode.HALF_UP);
                }
            }

            items.add(new AmortizationScheduleItem(
                    installment,
                    dueDate,
                    payment,
                    principalPaid,
                    interest,
                    remaining
            ));

            installment++;
        }

        LocalDate maturityDate = items.isEmpty() ? startDate : items.get(items.size() - 1).dueDate();

        return new DebtAmortizationResult(
                debt.getId(),
                debt.getName(),
                principal,
                targetPayment,
                totalInterest,
                maturityDate,
                items
        );
    }

    /**
     * Simulates debt payoff scenarios: Baseline vs. Avalanche vs. Snowball.
     * Pure function: does not alter the input list or debt entities.
     */
    public PayoffSimulationComparison simulatePayoff(List<Debt> debts, BigDecimal monthlySurplusCash, LocalDate asOfDate) {
        LocalDate startDate = asOfDate != null ? asOfDate : LocalDate.now(ZONE);
        BigDecimal surplus = monthlySurplusCash != null && monthlySurplusCash.compareTo(BigDecimal.ZERO) > 0
                ? monthlySurplusCash.setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

        PayoffStrategyResult baseline = simulateStrategy(debts, BigDecimal.ZERO, PayoffStrategy.BASELINE, startDate);
        PayoffStrategyResult avalanche = simulateStrategy(debts, surplus, PayoffStrategy.AVALANCHE, startDate);
        PayoffStrategyResult snowball = simulateStrategy(debts, surplus, PayoffStrategy.SNOWBALL, startDate);

        BigDecimal avalancheInterestSaved = baseline.totalInterestPaid().subtract(avalanche.totalInterestPaid()).max(BigDecimal.ZERO);
        int avalancheMonthsSaved = Math.max(0, baseline.totalMonths() - avalanche.totalMonths());

        BigDecimal snowballInterestSaved = baseline.totalInterestPaid().subtract(snowball.totalInterestPaid()).max(BigDecimal.ZERO);
        int snowballMonthsSaved = Math.max(0, baseline.totalMonths() - snowball.totalMonths());

        return new PayoffSimulationComparison(
                surplus,
                baseline,
                avalanche,
                snowball,
                avalancheInterestSaved,
                avalancheMonthsSaved,
                snowballInterestSaved,
                snowballMonthsSaved
        );
    }

    private PayoffStrategyResult simulateStrategy(
            List<Debt> originalDebts,
            BigDecimal monthlySurplusCash,
            PayoffStrategy strategy,
            LocalDate startDate
    ) {
        if (originalDebts == null || originalDebts.isEmpty()) {
            return new PayoffStrategyResult(strategy, startDate, 0, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, Collections.emptyList());
        }

        // Clone state into simulation holders
        List<SimDebt> debts = new ArrayList<>();
        BigDecimal totalCommittedMinimums = BigDecimal.ZERO;

        for (Debt d : originalDebts) {
            BigDecimal principal = d.getCurrentPrincipal() != null ? d.getCurrentPrincipal() : d.getOriginalPrincipal();
            if (principal != null && principal.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal minDue = d.getMonthlyMinimumDue() != null && d.getMonthlyMinimumDue().compareTo(BigDecimal.ZERO) > 0
                        ? d.getMonthlyMinimumDue()
                        : calculateAnnuityMonthlyPayment(principal, d.getInterestRateApr(), determineTermMonths(d, startDate));

                totalCommittedMinimums = totalCommittedMinimums.add(minDue);
                debts.add(new SimDebt(
                        d.getId(),
                        d.getName(),
                        principal.setScale(2, RoundingMode.HALF_UP),
                        normalizeApr(d.getInterestRateApr()),
                        minDue.setScale(2, RoundingMode.HALF_UP),
                        d.getInterestAccrualConvention() != null ? d.getInterestAccrualConvention() : InterestAccrualConvention.ACTUAL_365,
                        d.getDueDayOfMonth() > 0 ? d.getDueDayOfMonth() : 1
                ));
            }
        }

        if (debts.isEmpty()) {
            return new PayoffStrategyResult(strategy, startDate, 0, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, Collections.emptyList());
        }

        BigDecimal totalMonthlyPool = totalCommittedMinimums.add(monthlySurplusCash);
        int month = 0;
        LocalDate debtFreeDate = startDate;

        while (month < MAX_SIMULATION_MONTHS) {
            List<SimDebt> activeDebts = debts.stream().filter(SimDebt::isActive).toList();
            if (activeDebts.isEmpty()) {
                break;
            }

            month++;
            LocalDate simDate = startDate.plusMonths(month);
            BigDecimal totalMinimumsPaidThisMonth = BigDecimal.ZERO;

            // 1. Accrue interest and pay contractual minimums on all active debts
            for (SimDebt d : activeDebts) {
                BigDecimal periodicRate = computePeriodicRate(d.apr, d.convention, simDate);
                BigDecimal interest = d.balance.multiply(periodicRate).setScale(2, RoundingMode.HALF_UP);
                d.totalInterest = d.totalInterest.add(interest);

                BigDecimal payoffAmount = d.balance.add(interest);
                BigDecimal payment = d.minDue.min(payoffAmount);
                totalMinimumsPaidThisMonth = totalMinimumsPaidThisMonth.add(payment);

                BigDecimal principalPortion = payment.subtract(interest).max(BigDecimal.ZERO);
                d.balance = d.balance.subtract(principalPortion).setScale(2, RoundingMode.HALF_UP);
                d.totalPrincipal = d.totalPrincipal.add(principalPortion);

                if (d.balance.compareTo(BigDecimal.ZERO) <= 0) {
                    d.markPaidOff(month, simDate);
                    debtFreeDate = simDate;
                }
            }

            // 2. Allocate surplus pool (rollover + monthly surplus) for Avalanche and Snowball
            if (strategy != PayoffStrategy.BASELINE) {
                BigDecimal availableSurplus = totalMonthlyPool.subtract(totalMinimumsPaidThisMonth).max(BigDecimal.ZERO);

                while (availableSurplus.compareTo(BigDecimal.ZERO) > 0) {
                    SimDebt target = selectTargetDebt(debts, strategy);
                    if (target == null) {
                        break; // All debts paid off
                    }

                    BigDecimal extraPrincipal = availableSurplus.min(target.balance);
                    target.balance = target.balance.subtract(extraPrincipal).setScale(2, RoundingMode.HALF_UP);
                    target.totalPrincipal = target.totalPrincipal.add(extraPrincipal);
                    availableSurplus = availableSurplus.subtract(extraPrincipal);

                    if (target.balance.compareTo(BigDecimal.ZERO) <= 0) {
                        target.markPaidOff(month, simDate);
                        debtFreeDate = simDate;
                    }
                }
            }
        }

        BigDecimal grandTotalInterest = BigDecimal.ZERO;
        BigDecimal grandTotalPrincipal = BigDecimal.ZERO;
        List<DebtPayoffDetail> details = new ArrayList<>();

        for (SimDebt d : debts) {
            grandTotalInterest = grandTotalInterest.add(d.totalInterest);
            grandTotalPrincipal = grandTotalPrincipal.add(d.totalPrincipal);
            details.add(new DebtPayoffDetail(
                    d.id,
                    d.name,
                    d.startingPrincipal,
                    d.totalInterest,
                    d.totalPrincipal,
                    d.payoffMonth,
                    d.payoffDate != null ? d.payoffDate : startDate.plusMonths(month)
            ));
        }

        BigDecimal grandTotalPayments = grandTotalInterest.add(grandTotalPrincipal);

        return new PayoffStrategyResult(
                strategy,
                debtFreeDate,
                month,
                grandTotalInterest,
                grandTotalPrincipal,
                grandTotalPayments,
                details
        );
    }

    private SimDebt selectTargetDebt(List<SimDebt> debts, PayoffStrategy strategy) {
        List<SimDebt> active = debts.stream().filter(SimDebt::isActive).toList();
        if (active.isEmpty()) return null;

        if (strategy == PayoffStrategy.AVALANCHE) {
            // Highest APR first; break ties with smallest balance
            return active.stream().max(
                    Comparator.comparing(SimDebt::getApr)
                            .thenComparing(Comparator.comparing(SimDebt::getBalance).reversed())
            ).orElse(null);
        } else if (strategy == PayoffStrategy.SNOWBALL) {
            // Smallest balance first; break ties with highest APR
            return active.stream().min(
                    Comparator.comparing(SimDebt::getBalance)
                            .thenComparing(Comparator.comparing(SimDebt::getApr).reversed())
            ).orElse(null);
        }

        return null;
    }

    private BigDecimal computePeriodicRate(BigDecimal effectiveApr, InterestAccrualConvention convention, LocalDate date) {
        if (effectiveApr == null || effectiveApr.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }

        if (convention == InterestAccrualConvention.ACTUAL_365) {
            int daysInMonth = date.lengthOfMonth();
            return effectiveApr.multiply(BigDecimal.valueOf(daysInMonth))
                    .divide(BigDecimal.valueOf(365), 10, RoundingMode.HALF_UP);
        } else {
            // THIRTY_360 or MONTHLY_FLAT
            return effectiveApr.divide(BigDecimal.valueOf(12), 10, RoundingMode.HALF_UP);
        }
    }

    private int determineTermMonths(Debt debt, LocalDate startDate) {
        if (debt.getMaturityDate() != null) {
            Period p = Period.between(startDate, debt.getMaturityDate());
            int months = p.getYears() * 12 + p.getMonths();
            return Math.max(1, months);
        }
        return 12; // Default 1 year if unspecified
    }

    private BigDecimal normalizeApr(BigDecimal apr) {
        if (apr == null || apr.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        // If user entered 8.5 for 8.5%, normalize to 0.085
        return apr.compareTo(BigDecimal.ONE) > 0
                ? apr.divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP)
                : apr;
    }

    private static class SimDebt {
        final UUID id;
        final String name;
        final BigDecimal startingPrincipal;
        final BigDecimal apr;
        final BigDecimal minDue;
        final InterestAccrualConvention convention;
        final int dueDay;

        BigDecimal balance;
        BigDecimal totalInterest = BigDecimal.ZERO;
        BigDecimal totalPrincipal = BigDecimal.ZERO;
        int payoffMonth = 0;
        LocalDate payoffDate = null;
        boolean paidOff = false;

        SimDebt(UUID id, String name, BigDecimal startingPrincipal, BigDecimal apr, BigDecimal minDue,
                InterestAccrualConvention convention, int dueDay) {
            this.id = id;
            this.name = name;
            this.startingPrincipal = startingPrincipal;
            this.apr = apr;
            this.minDue = minDue;
            this.convention = convention;
            this.dueDay = dueDay;
            this.balance = startingPrincipal;
        }

        boolean isActive() {
            return !paidOff && balance.compareTo(BigDecimal.ZERO) > 0;
        }

        void markPaidOff(int month, LocalDate date) {
            this.paidOff = true;
            this.payoffMonth = month;
            this.payoffDate = date;
            this.balance = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }

        BigDecimal getApr() { return apr; }
        BigDecimal getBalance() { return balance; }
    }
}
