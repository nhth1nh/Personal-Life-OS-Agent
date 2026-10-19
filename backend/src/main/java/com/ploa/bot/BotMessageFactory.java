package com.ploa.bot;

import com.ploa.ai.dto.OcrResult;
import com.ploa.finance.model.Transaction;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;

import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

@Component
public class BotMessageFactory {

    private static final NumberFormat VND_FORMAT = NumberFormat.getInstance(new Locale("vi", "VN"));

    public SendMessage buildConfirmationMessage(long chatId, Transaction tx, OcrResult ocr) {
        String merchant = escapeHtml(tx.getMerchant() != null ? tx.getMerchant() : "—");
        String category = escapeHtml(tx.getCategory() != null ? tx.getCategory() : "Other");
        String note = escapeHtml(tx.getNote() != null ? tx.getNote() : "—");

        String text = """
                📋 <b>Transaction Captured</b>
                
                💰 Amount: <b>%s VND</b>
                🏷️ Category: <b>%s</b> (%s)
                🏪 Merchant: %s
                📅 Date: %s
                📝 Note: %s
                
                <i>Please confirm or correct:</i>
                """.formatted(
                VND_FORMAT.format(tx.getAmount()),
                category,
                tx.getSpendType(),
                merchant,
                tx.getTxDate(),
                note
        );

        String txId = tx.getId().toString();

        InlineKeyboardMarkup keyboard = InlineKeyboardMarkup.builder()
                .keyboardRow(new InlineKeyboardRow(List.of(
                        InlineKeyboardButton.builder()
                                .text("✅ Confirm")
                                .callbackData("CONFIRM:" + txId)
                                .build(),
                        InlineKeyboardButton.builder()
                                .text("🔄 Toggle Type")
                                .callbackData("TOGGLE_TYPE:" + txId)
                                .build(),
                        InlineKeyboardButton.builder()
                                .text("🗑️ Delete")
                                .callbackData("DELETE:" + txId)
                                .build()
                )))
                .build();

        return SendMessage.builder()
                .chatId(chatId)
                .text(text)
                .parseMode("HTML")
                .replyMarkup(keyboard)
                .build();
    }

    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    public String buildHealthSummary(com.ploa.health.engine.dto.ReadinessResult r,
                                     com.ploa.health.engine.dto.WeightEmaResult w) {
        String hrvZ = r.hrvZScore() != null ? String.format("%+.2f", r.hrvZScore()) : "N/A";
        String rhrZ = r.rhrZScore() != null ? String.format("%+.2f", r.rhrZScore()) : "N/A";
        String hrvAvg = r.hrv30dMean() != null ? String.format("%.1f", r.hrv30dMean()) : "N/A";
        String rhrAvg = r.rhr30dMean() != null ? String.format("%.1f", r.rhr30dMean()) : "N/A";
        String hrvVal = r.hrvRmssd() != null ? String.format("%.0f", r.hrvRmssd()) : "N/A";
        String rhrVal = r.rhr() != null ? String.valueOf(r.rhr()) : "N/A";
        String deltaKg = w.deltaToTargetKg() != null ? String.format("%+.1f", w.deltaToTargetKg()) : "N/A";
        String trendKg = w.weeklyTrendKg() != null ? String.format("%+.2f kg/wk", w.weeklyTrendKg()) : "N/A";

        return """
                🏥 *Daily Health & Recovery Report*
                📅 Date: *%s*
                
                🏃 *Readiness: %s*
                • HRV rMSSD: *%s ms* (Baseline: %s ms, Z: *%s*)
                • Resting HR: *%s bpm* (Baseline: %s bpm, Z: *%s*)
                • Sleep Total: *%d min* (Debt: *%d min*)
                
                ⚖️ *Weight Trend:*
                • Latest: *%.1f kg* (7-day EMA: *%.1f kg*)
                • Target: *%.1f kg* (Delta: *%s kg*)
                • Weekly Trend: *%s*
                
                💡 _%s_
                """.formatted(
                r.date(),
                r.level(),
                hrvVal, hrvAvg, hrvZ,
                rhrVal, rhrAvg, rhrZ,
                r.sleepTotalMin() != null ? r.sleepTotalMin() : 0,
                r.sleepDebtMin() != null ? r.sleepDebtMin() : 0,
                w.latestWeightKg() != null ? w.latestWeightKg() : 0.0,
                w.ema7Kg() != null ? w.ema7Kg() : 0.0,
                w.targetWeightKg() != null ? w.targetWeightKg() : 0.0,
                deltaKg,
                trendKg,
                r.summary()
        );
    }

    public String buildFinanceSummary(com.ploa.finance.engine.dto.BurnRateResult f) {
        return """
                💰 *Monthly Financial Report*
                📅 Progress: *%d / %d days* (%d days left)
                
                📊 *Budget Overview:*
                • Monthly Budget: *%s VND*
                • MTD Total Spend: *%s VND*
                • Discretionary: *%s VND* | Fixed: *%s VND*
                • Remaining Budget: *%s VND*
                
                ⚡ *Pacing & Allowance:*
                • Daily Burn Rate: *%s VND/day*
                • Safe Daily Allowance: *%s VND/day*
                • Savings Rate: *%.1f%%*
                """.formatted(
                f.daysElapsed(),
                f.daysElapsed() + f.daysRemaining(),
                f.daysRemaining(),
                VND_FORMAT.format(f.monthlyBudgetVnd()),
                VND_FORMAT.format(f.mtdTotalSpendVnd()),
                VND_FORMAT.format(f.mtdDiscretionaryVnd()),
                VND_FORMAT.format(f.mtdFixedVnd()),
                VND_FORMAT.format(f.remainingBudgetVnd()),
                VND_FORMAT.format((long) f.dailyBurnRateVnd()),
                VND_FORMAT.format((long) f.safeDailyAllowanceVnd()),
                f.monthlySavingsRatePct()
        );
    }

    public String helpText() {
        return """
                🤖 *Personal Life OS Agent*
                
                *Commands:*
                /health — Today's HRV/RHR recovery + weight EMA
                /finance — Month-to-date spending & burn rate
                /briefing — Generate and send today's AI morning briefing
                /sync — Pull latest live biometrics from Garmin Connect
                /help — Show this message
                
                *Quick Expense:*
                Send a photo of a receipt, transfer, or online checkout.
                Or type: `50k coffee`, `120k groceries`, `200k electricity`
                """;
    }
}
