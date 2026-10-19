package com.ploa.bot;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ploa.ai.VisionOcrService;
import com.ploa.ai.dto.OcrResult;
import com.ploa.assistant.AssistantService;
import com.ploa.briefing.BriefingOrchestrator;
import com.ploa.config.PloaProperties;
import com.ploa.finance.model.SpendType;
import com.ploa.finance.model.Transaction;
import com.ploa.finance.service.FinanceService;
import com.ploa.health.service.HealthService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.longpolling.interfaces.LongPollingUpdateConsumer;
import org.telegram.telegrambots.longpolling.starter.SpringLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.GetFile;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.Document;
import org.telegram.telegrambots.meta.api.objects.File;
import org.telegram.telegrambots.meta.api.objects.PhotoSize;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Telegram Long-Polling Bot Handler.
 * Handles:
 *  - Photo messages → Vision OCR → save draft transaction → inline keyboard
 *  - Text messages → quick expense parsing or Life OS conversational AI assistant
 *  - Callback queries → confirm, change category, delete
 */
@Component
public class TelegramBotHandler implements SpringLongPollingBot, LongPollingUpdateConsumer {

    private static final Logger log = LoggerFactory.getLogger(TelegramBotHandler.class);
    private static final ObjectMapper MAPPER = new ObjectMapper().findAndRegisterModules();

    private final TelegramClient telegramClient;
    private final PloaProperties properties;
    private final VisionOcrService ocrService;
    private final FinanceService financeService;
    private final HealthService healthService;
    private final BriefingOrchestrator briefingOrchestrator;
    private final AssistantService assistantService;
    private final BotMessageFactory messageFactory;

    public TelegramBotHandler(PloaProperties properties,
                               VisionOcrService ocrService,
                               FinanceService financeService,
                               HealthService healthService,
                               BriefingOrchestrator briefingOrchestrator,
                               AssistantService assistantService,
                               BotMessageFactory messageFactory) {
        this.properties = properties;
        this.ocrService = ocrService;
        this.financeService = financeService;
        this.healthService = healthService;
        this.briefingOrchestrator = briefingOrchestrator;
        this.assistantService = assistantService;
        this.messageFactory = messageFactory;
        String token = properties.telegram().botToken();
        this.telegramClient = (token != null && !token.isBlank())
                ? new OkHttpTelegramClient(token)
                : null;
    }

    @Override
    public String getBotToken() {
        String token = properties.telegram().botToken();
        return token != null ? token : "";
    }

    @Override
    public LongPollingUpdateConsumer getUpdatesConsumer() {
        return this;
    }

    @Override
    public void consume(List<Update> updates) {
        updates.forEach(this::processUpdate);
    }

    private void processUpdate(Update update) {
        if (telegramClient == null) {
            log.debug("Telegram client not configured; ignoring update");
            return;
        }
        try {
            if (update.hasMessage()) {
                long chatId = update.getMessage().getChatId();
                if (!isAllowed(chatId)) {
                    log.warn("Rejected update from unauthorized chat_id: {}", chatId);
                    return;
                }
                if (update.getMessage().hasPhoto()) {
                    handlePhoto(update);
                } else if (update.getMessage().hasDocument() && isImageDocument(update.getMessage().getDocument())) {
                    handleDocumentPhoto(update);
                } else if (update.getMessage().hasText()) {
                    handleText(update);
                }
            } else if (update.hasCallbackQuery()) {
                handleCallback(update);
            }
        } catch (Exception e) {
            log.error("Error processing update: {}", e.getMessage(), e);
        }
    }

    // ── Photo handler ─────────────────────────────────────────────────────────

    private void handlePhoto(Update update) throws TelegramApiException {
        long chatId = update.getMessage().getChatId();
        long msgId = update.getMessage().getMessageId();

        sendText(chatId, "🔍 Analyzing receipt...");

        // Get highest resolution photo
        PhotoSize photo = update.getMessage().getPhoto().stream()
                .max(Comparator.comparingInt(PhotoSize::getFileSize))
                .orElseThrow();

        // Download file bytes directly from Telegram to prevent remote mime-type mismatch
        File file = telegramClient.execute(GetFile.builder().fileId(photo.getFileId()).build());
        byte[] photoBytes;
        try (InputStream is = telegramClient.downloadFileAsStream(file)) {
            photoBytes = is.readAllBytes();
        } catch (IOException e) {
            log.error("Failed to read photo stream from Telegram: {}", e.getMessage(), e);
            sendText(chatId, "❌ Failed to download photo from Telegram.");
            return;
        }

        OcrResult ocr = ocrService.extractFromImage(photoBytes, "image/jpeg");
        Transaction tx = buildTransaction(ocr, msgId);
        Transaction saved = financeService.save(tx);

        SendMessage msg = messageFactory.buildConfirmationMessage(chatId, saved, ocr);
        telegramClient.execute(msg);
    }

    private void handleDocumentPhoto(Update update) throws TelegramApiException {
        long chatId = update.getMessage().getChatId();
        long msgId = update.getMessage().getMessageId();
        Document doc = update.getMessage().getDocument();

        sendText(chatId, "🔍 Analyzing receipt document...");

        File file = telegramClient.execute(GetFile.builder().fileId(doc.getFileId()).build());
        byte[] photoBytes;
        try (InputStream is = telegramClient.downloadFileAsStream(file)) {
            photoBytes = is.readAllBytes();
        } catch (IOException e) {
            log.error("Failed to read document stream from Telegram: {}", e.getMessage(), e);
            sendText(chatId, "❌ Failed to download document from Telegram.");
            return;
        }

        String mime = (doc.getMimeType() != null && !doc.getMimeType().isBlank())
                ? doc.getMimeType()
                : "image/jpeg";
        OcrResult ocr = ocrService.extractFromImage(photoBytes, mime);
        Transaction tx = buildTransaction(ocr, msgId);
        Transaction saved = financeService.save(tx);

        SendMessage msg = messageFactory.buildConfirmationMessage(chatId, saved, ocr);
        telegramClient.execute(msg);
    }

    private boolean isImageDocument(Document doc) {
        if (doc == null) return false;
        String mime = doc.getMimeType();
        return mime != null && mime.startsWith("image/");
    }

    // ── Text handler ──────────────────────────────────────────────────────────

    private void handleText(Update update) throws TelegramApiException {
        long chatId = update.getMessage().getChatId();
        String text = update.getMessage().getText().trim();

        if (text.startsWith("/")) {
            handleCommand(chatId, text);
            return;
        }

        if (isExpenseIntent(text)) {
            // Quick expense: "50k coffee", "45k lunch"
            sendText(chatId, "💬 Parsing expense...");
            OcrResult ocr = ocrService.extractFromText(text);
            Transaction tx = buildTransaction(ocr, null);
            Transaction saved = financeService.save(tx);
            SendMessage msg = messageFactory.buildConfirmationMessage(chatId, saved, ocr);
            telegramClient.execute(msg);
        } else {
            // Life OS AI Assistant chat!
            sendText(chatId, "🤔 *PLOA is thinking...*");
            String reply = assistantService.chat(text);
            sendText(chatId, reply);
        }
    }

    private boolean isExpenseIntent(String text) {
        String t = text.trim();
        // Explicit expense command
        if (t.matches("(?i)^/(expense|spend|pay)\\s+.*")) return true;
        // If it looks like a question or conversational prompt, treat as chat
        if (t.contains("?") || t.matches("(?i).*(what|how|why|should|can i|is it|status|overview|tell me|explain|hello|hi).*")) {
            return false;
        }
        // Typical expense patterns:
        // 1. Contains a number + currency token: "50k", "200k", "1.5m", "100 usd", "50 vnd"
        // 2. Starts with number + space + text: "50 coffee"
        return t.matches("(?i).*\\b\\d+([.,]\\d+)?\\s*(k|vnd|usd|eur|m)?\\b.*")
                || t.matches("^\\d+([.,]\\d+)?\\s+[a-zA-Z].*");
    }

    // ── Command handler ───────────────────────────────────────────────────────

    private void handleCommand(long chatId, String command) throws TelegramApiException {
        switch (command.split("@")[0].toLowerCase()) {
            case "/start", "/help" -> sendText(chatId, messageFactory.helpText());
            case "/health" -> {
                LocalDate today = LocalDate.now();
                var readiness = healthService.computeReadiness(today);
                var weight = healthService.computeWeightEma();
                sendText(chatId, messageFactory.buildHealthSummary(readiness, weight));
            }
            case "/finance" -> {
                var burnRate = financeService.getBurnRate();
                sendText(chatId, messageFactory.buildFinanceSummary(burnRate));
            }
            case "/briefing" -> {
                sendText(chatId, "⏳ *Synthesizing daily briefing with AI...*");
                String briefing = briefingOrchestrator.generateBriefingPreview();
                sendText(chatId, briefing);
            }
            case "/sync" -> {
                sendText(chatId, "⏳ *Pulling live data from Garmin Connect...*");
                try {
                    var rest = org.springframework.web.client.RestClient.create();
                    rest.post().uri("http://garmin-puller:8081/pull").retrieve().toBodilessEntity();
                    var m = healthService.getMetricsForDate(LocalDate.now());
                    String syncMsg = """
                            ✅ *Garmin Sync Complete!*
                            • 🔋 Body Battery: %s/100
                            • ⚡ Stress: %s/100 (%s)
                            • 👟 Steps: %,d
                            • 🔥 Active Kcal: %s kcal (Total: %s kcal)
                            • ⏱️ Active Time: %s min
                            • ❤️ Resting HR: %s bpm (Min: %s, Max: %s)
                            """.formatted(
                            m != null && m.getBodyBatteryCurrent() != null ? m.getBodyBatteryCurrent() : "N/A",
                            m != null && m.getStressAvg() != null ? m.getStressAvg() : "N/A",
                            m != null && m.getStressQualifier() != null ? m.getStressQualifier() : "N/A",
                            m != null && m.getSteps() != null ? m.getSteps() : 0,
                            m != null && m.getActiveKcal() != null ? m.getActiveKcal() : "N/A",
                            m != null && m.getTotalKcal() != null ? m.getTotalKcal() : "N/A",
                            m != null && m.getActiveTimeMin() != null ? m.getActiveTimeMin() : "N/A",
                            m != null && m.getRhr() != null ? m.getRhr() : "N/A",
                            m != null && m.getMinHr() != null ? m.getMinHr() : "N/A",
                            m != null && m.getMaxHr() != null ? m.getMaxHr() : "N/A"
                    );
                    sendText(chatId, syncMsg);
                } catch (Exception e) {
                    log.error("Telegram /sync failed: {}", e.getMessage());
                    sendText(chatId, "❌ Failed to sync from Garmin: " + e.getMessage());
                }
            }
            default -> sendText(chatId, "Unknown command. Try /help");
        }
    }

    // ── Callback query handler ─────────────────────────────────────────────────

    private void handleCallback(Update update) throws TelegramApiException {
        var cb = update.getCallbackQuery();
        long chatId = cb.getMessage().getChatId();
        int msgId = cb.getMessage().getMessageId();
        String data = cb.getData();

        if (!isAllowed(chatId)) return;

        String[] parts = data.split(":");
        String action = parts[0];
        UUID txId = UUID.fromString(parts[1]);

        switch (action) {
            case "CONFIRM" -> {
                financeService.confirm(txId);
                editText(chatId, msgId, "✅ Transaction confirmed!");
            }
            case "DELETE" -> {
                financeService.delete(txId);
                editText(chatId, msgId, "🗑️ Transaction deleted.");
            }
            case "TOGGLE_TYPE" -> {
                String newCategory = parts.length > 2 ? parts[2] : "Other";
                SpendType newType = SpendType.valueOf(parts.length > 3 ? parts[3] : "DISCRETIONARY");
                Transaction updated = financeService.updateCategory(txId, newCategory, newType);
                editText(chatId, msgId,
                        "🔄 Updated: " + updated.getCategory() + " (" + updated.getSpendType() + ")");
            }
            default -> log.warn("Unknown callback action: {}", action);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Transaction buildTransaction(OcrResult ocr, Long telegramMsgId) {
        Transaction tx = new Transaction();
        tx.setTxDate(ocr.transactionDate() != null ? ocr.transactionDate() : LocalDate.now());
        tx.setAmount(ocr.totalAmount() != null ? ocr.totalAmount() : 0L);
        tx.setCurrency(ocr.currency() != null ? ocr.currency() : "VND");
        tx.setCategory(ocr.category() != null ? ocr.category() : "Other");
        tx.setSpendType("FIXED".equals(ocr.spendType()) ? SpendType.FIXED : SpendType.DISCRETIONARY);
        tx.setMerchant(ocr.merchant());
        tx.setNote(ocr.note());
        tx.setDocumentType(ocr.documentType());
        tx.setSourcePlatform(ocr.sourcePlatform());
        if (ocr.lineItems() != null && !ocr.lineItems().isEmpty()) {
            tx.setItemsJsonb(MAPPER.valueToTree(ocr.lineItems()));
        }
        tx.setTelegramMsgId(telegramMsgId);
        tx.setConfirmed(false);
        return tx;
    }

    private boolean isAllowed(long chatId) {
        List<Long> allowed = properties.telegram().allowedChatIdList();
        return allowed.isEmpty() || allowed.contains(chatId);
    }

    private void sendText(long chatId, String text) throws TelegramApiException {
        if (telegramClient == null) {
            log.warn("TelegramClient not initialized, cannot send text");
            return;
        }
        String html = TelegramFormatter.markdownToHtml(text);
        try {
            telegramClient.execute(SendMessage.builder()
                    .chatId(chatId)
                    .text(html)
                    .parseMode("HTML")
                    .build());
        } catch (TelegramApiException e) {
            log.debug("HTML send failed ({}), retrying without parse mode", e.getMessage());
            telegramClient.execute(SendMessage.builder()
                    .chatId(chatId)
                    .text(text)
                    .build());
        }
    }

    private void editText(long chatId, int msgId, String text) throws TelegramApiException {
        if (telegramClient == null) {
            log.warn("TelegramClient not initialized, cannot edit text");
            return;
        }
        String html = TelegramFormatter.markdownToHtml(text);
        try {
            telegramClient.execute(EditMessageText.builder()
                    .chatId(chatId)
                    .messageId(msgId)
                    .text(html)
                    .parseMode("HTML")
                    .build());
        } catch (TelegramApiException e) {
            log.debug("HTML edit failed ({}), retrying without parse mode", e.getMessage());
            telegramClient.execute(EditMessageText.builder()
                    .chatId(chatId)
                    .messageId(msgId)
                    .text(text)
                    .build());
        }
    }
}
