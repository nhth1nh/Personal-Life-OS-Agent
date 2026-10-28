package com.ploa.bot;

import com.ploa.ai.dto.OcrResult;
import com.ploa.finance.model.Transaction;
import com.ploa.profile.UserProfileService;
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
        String timeStr = tx.getTxTimestamp() != null
                ? tx.getTxTimestamp().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
                : tx.getTxDate().toString();

        StringBuilder sb = new StringBuilder();
        if (tx.getDuplicateWarning() != null && !tx.getDuplicateWarning().isBlank()) {
            sb.append("⚠️ <b>Potential Duplicate Detected!</b>\n");
            sb.append("🔍 <i>").append(escapeHtml(tx.getDuplicateWarning())).append("</i>\n\n");
        } else {
            sb.append("📋 <b>Transaction Captured</b>\n\n");
        }

        sb.append("💰 Amount: <b>").append(VND_FORMAT.format(tx.getAmount())).append(" VND</b>\n");
        sb.append("🏷️ Category: <b>").append(category).append("</b> (").append(tx.getSpendType()).append(")\n");
        sb.append("🏪 Merchant: ").append(merchant).append("\n");
        sb.append("📅 Timestamp: ").append(timeStr).append("\n");
        if (tx.getInvoiceNumber() != null && !tx.getInvoiceNumber().isBlank()) {
            sb.append("🧾 Invoice/Ref: <code>").append(escapeHtml(tx.getInvoiceNumber())).append("</code>\n");
        }
        sb.append("📝 Note: ").append(note).append("\n\n");
        sb.append("<i>💡 Tap ✏️ Edit or reply to this message to correct (e.g. 'amount 45k', 'merchant Starbucks').</i>");

        String txId = tx.getId().toString();
        String confirmText = tx.getDuplicateWarning() != null ? "✅ Confirm Anyway" : "✅ Confirm";

        InlineKeyboardMarkup keyboard = InlineKeyboardMarkup.builder()
                .keyboardRow(new InlineKeyboardRow(List.of(
                        InlineKeyboardButton.builder()
                                .text(confirmText)
                                .callbackData("CONFIRM:" + txId)
                                .build(),
                        InlineKeyboardButton.builder()
                                .text("✏️ Edit")
                                .callbackData("EDIT:" + txId)
                                .build()
                )))
                .keyboardRow(new InlineKeyboardRow(List.of(
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
                .text(sb.toString())
                .parseMode("HTML")
                .replyMarkup(keyboard)
                .build();
    }

    public SendMessage buildImageIntentMessage(long chatId, String pendingId) {
        InlineKeyboardMarkup keyboard = InlineKeyboardMarkup.builder()
                .keyboardRow(new InlineKeyboardRow(List.of(
                        InlineKeyboardButton.builder()
                                .text("🧾 Log as Expense")
                                .callbackData("IMG_RECEIPT:" + pendingId)
                                .build(),
                        InlineKeyboardButton.builder()
                                .text("💬 Ask Assistant")
                                .callbackData("IMG_CHAT:" + pendingId)
                                .build()
                )))
                .keyboardRow(new InlineKeyboardRow(List.of(
                        InlineKeyboardButton.builder()
                                .text("❌ Cancel")
                                .callbackData("IMG_CANCEL:" + pendingId)
                                .build()
                )))
                .build();

        return SendMessage.builder()
                .chatId(chatId)
                .text("🤔 <b>What would you like to do with this image?</b>")
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

    public String buildProfileSummary(UserProfileService.UserProfileData p) {
        String name = p.userName() != null && !p.userName().isBlank() ? p.userName() : "Not set";
        return """
                ⚙️ *Personal Life OS — Base Profile & Targets*
                
                • 💰 *Monthly Budget:* %s VND
                • ⚖️ *Target Weight:* %.1f kg
                • 🛌 *Sleep Target:* %.1f hours/night
                • 📅 *Baseline Window:* %d days
                • 👤 *User Name:* %s
                
                💡 *How to update:*
                • `/init budget 15M` (or `15000000`, `15tr`)
                • `/init weight 65` (or `65.5kg`)
                • `/init sleep 8` (or `8h`)
                • `/init budget 15M weight 65 sleep 8`
                
                _You can also update anytime simply by chatting with the AI (e.g. "Đặt ngân sách 15 triệu")._
                """.formatted(
                VND_FORMAT.format(p.monthlyBudgetVnd()),
                p.targetWeightKg(),
                p.sleepTargetHours(),
                p.baselineWindowDays(),
                name
        );
    }

    public String buildProfileUpdateSuccess(UserProfileService.UserProfileData p) {
        String name = p.userName() != null && !p.userName().isBlank() ? p.userName() : "Not set";
        return """
                ✅ *PLOA Profile & Base Targets Updated!*
                
                • 💰 *Monthly Budget:* %s VND
                • ⚖️ *Target Weight:* %.1f kg
                • 🛌 *Sleep Target:* %.1f hours/night
                • 📅 *Baseline Window:* %d days
                • 👤 *User Name:* %s
                
                _All calculations across /finance, /health, and daily briefings now use these dynamic targets._
                """.formatted(
                VND_FORMAT.format(p.monthlyBudgetVnd()),
                p.targetWeightKg(),
                p.sleepTargetHours(),
                p.baselineWindowDays(),
                name
        );
    }

    public String helpText() {
        return """
                🤖 *Personal Life OS Agent*
                
                *Commands:*
                /init — View or configure base data (budget, target weight, sleep goal)
                /health — Today's HRV/RHR recovery + weight EMA
                /finance — Month-to-date spending & burn rate
                /briefing — Generate and send today's AI morning briefing (07:30)
                /evening — Generate and send today's AI evening summary (21:30)
                /sync — Pull latest live biometrics from Garmin Connect
                /new — Clear chat memory & start a fresh conversation
                /help — Show this message
                
                *Quick Expense:*
                Send a photo of a receipt, transfer, or online checkout.
                Or type: `50k coffee`, `120k groceries`, `200k electricity`
                """;
    }
}
