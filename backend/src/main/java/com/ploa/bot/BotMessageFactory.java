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
        String text = """
                📋 *Transaction Captured*
                
                💰 Amount: *%s VND*
                🏷️ Category: *%s* (%s)
                🏪 Merchant: %s
                📅 Date: %s
                📝 Note: %s
                
                _Please confirm or correct:_
                """.formatted(
                VND_FORMAT.format(tx.getAmount()),
                tx.getCategory(),
                tx.getSpendType(),
                tx.getMerchant() != null ? tx.getMerchant() : "—",
                tx.getTxDate(),
                tx.getNote() != null ? tx.getNote() : "—"
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
                .parseMode("Markdown")
                .replyMarkup(keyboard)
                .build();
    }

    public String helpText() {
        return """
                🤖 *Personal Life OS Agent*
                
                *Commands:*
                /health — 7-day HRV/RHR Z-score + weight EMA
                /finance — MTD spend & category breakdown
                /help — Show this message
                
                *Quick Expense:*
                Send a photo of a receipt, bank transfer, or Shopee order.
                Or type: `50k cafe`, `120k cầu lông`, `200k điện`
                """;
    }
}
