package com.ploa.ai.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.ploa.finance.model.DebtType;

import java.time.LocalDate;

/**
 * Structured output from Vision OCR perception for loans, credit card statements, and BNPL agreements.
 * Raw extracted entities: math/rates are verified deterministically by Java engines.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DebtOcrResult(
        @JsonProperty("document_type") String documentType,
        @JsonProperty("creditor") String creditor,
        @JsonProperty("debt_name") @JsonAlias({"debtName", "loan_name", "product_name"}) String debtName,
        @JsonProperty("account_number") @JsonAlias({"accountNumber", "contract_number", "contractNumber", "so_tai_khoan", "tai_khoan_vay", "account_no", "accountNo"}) String accountNumber,
        @JsonProperty("debt_type") DebtType debtType,
        @JsonProperty("original_principal") @JsonAlias({"originalPrincipal", "total_borrowed", "limit_used", "total_amount"}) Long originalPrincipal,
        @JsonProperty("current_principal") @JsonAlias({"currentPrincipal", "outstanding_balance", "statement_balance", "du_no_hien_tai"}) Long currentPrincipal,
        @JsonProperty("monthly_payment") @JsonAlias({"monthlyPayment", "minimum_due", "installment_amount", "so_tien_thanh_toan_toi_thieu"}) Long monthlyPayment,
        @JsonProperty("term_months") @JsonAlias({"termMonths", "total_tenure", "total_installments", "so_ky"}) Integer termMonths,
        @JsonProperty("remaining_months") @JsonAlias({"remainingMonths", "remaining_installments", "so_ky_con_lai"}) Integer remainingMonths,
        @JsonProperty("stated_interest_rate") @JsonAlias({"statedInterestRate", "interest_rate_str", "lai_suat"}) String statedInterestRate,
        @JsonProperty("stated_apr") @JsonAlias({"statedApr", "apr_annual"}) Double statedApr,
        @JsonProperty("due_day_of_month") @JsonAlias({"dueDayOfMonth", "due_day"}) Integer dueDayOfMonth,
        @JsonProperty("due_date") @JsonAlias({"dueDate", "payment_due_date", "ngay_den_han"}) LocalDate dueDate,
        @JsonProperty("origination_date") @JsonAlias({"originationDate", "disbursement_date", "ngay_giai_ngan"}) LocalDate originationDate,
        @JsonProperty("statement_date") @JsonAlias({"statementDate", "ngay_sao_ke"}) LocalDate statementDate,
        @JsonProperty("is_payment_slip") @JsonAlias({"isPaymentSlip", "is_repayment"}) Boolean isPaymentSlip,
        @JsonProperty("payment_amount") @JsonAlias({"paymentAmount", "amount_paid"}) Long paymentAmount,
        @JsonProperty("note") String note,
        @JsonProperty("confidence") Double confidence
) {
    /**
     * Backwards-compatible convenience constructor without accountNumber.
     */
    public DebtOcrResult(
            String documentType,
            String creditor,
            String debtName,
            DebtType debtType,
            Long originalPrincipal,
            Long currentPrincipal,
            Long monthlyPayment,
            Integer termMonths,
            Integer remainingMonths,
            String statedInterestRate,
            Double statedApr,
            Integer dueDayOfMonth,
            LocalDate dueDate,
            LocalDate originationDate,
            LocalDate statementDate,
            Boolean isPaymentSlip,
            Long paymentAmount,
            String note,
            Double confidence
    ) {
        this(documentType, creditor, debtName, null, debtType, originalPrincipal, currentPrincipal,
                monthlyPayment, termMonths, remainingMonths, statedInterestRate, statedApr,
                dueDayOfMonth, dueDate, originationDate, statementDate, isPaymentSlip, paymentAmount, note, confidence);
    }

    /**
     * Backwards-compatible convenience constructor without originationDate and accountNumber.
     */
    public DebtOcrResult(
            String documentType,
            String creditor,
            String debtName,
            DebtType debtType,
            Long originalPrincipal,
            Long currentPrincipal,
            Long monthlyPayment,
            Integer termMonths,
            Integer remainingMonths,
            String statedInterestRate,
            Double statedApr,
            Integer dueDayOfMonth,
            LocalDate dueDate,
            LocalDate statementDate,
            Boolean isPaymentSlip,
            Long paymentAmount,
            String note,
            Double confidence
    ) {
        this(documentType, creditor, debtName, null, debtType, originalPrincipal, currentPrincipal,
                monthlyPayment, termMonths, remainingMonths, statedInterestRate, statedApr,
                dueDayOfMonth, dueDate, null, statementDate, isPaymentSlip, paymentAmount, note, confidence);
    }
}
