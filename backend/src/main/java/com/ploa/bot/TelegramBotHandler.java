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
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
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
import java.util.Optional;
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
    private static final long MAX_FILE_SIZE_BYTES = 20 * 1024 * 1024; // 20 MB cap to prevent OOM

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
        String caption = update.getMessage().getCaption();

        // Get highest resolution photo
        PhotoSize photo = update.getMessage().getPhoto().stream()
                .max(Comparator.comparingInt(PhotoSize::getFileSize))
                .orElseThrow();

        if (photo.getFileSize() != null && photo.getFileSize() > MAX_FILE_SIZE_BYTES) {
            log.warn("Photo size {} bytes exceeds limit of {} bytes", photo.getFileSize(), MAX_FILE_SIZE_BYTES);
            sendText(chatId, "❌ Image is too large (maximum allowable size is 20MB).");
            return;
        }

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

        processIncomingImage(chatId, msgId, photoBytes, "image/jpeg", caption);
    }

    private void handleDocumentPhoto(Update update) throws TelegramApiException {
        long chatId = update.getMessage().getChatId();
        long msgId = update.getMessage().getMessageId();
        Document doc = update.getMessage().getDocument();
        String caption = update.getMessage().getCaption();

        if (doc.getFileSize() != null && doc.getFileSize() > MAX_FILE_SIZE_BYTES) {
            log.warn("Document size {} bytes exceeds limit of {} bytes", doc.getFileSize(), MAX_FILE_SIZE_BYTES);
            sendText(chatId, "❌ Document is too large (maximum allowable size is 20MB).");
            return;
        }

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
        processIncomingImage(chatId, msgId, photoBytes, mime, caption);
    }

    private void processIncomingImage(long chatId, long msgId, byte[] photoBytes, String mimeType, String caption) throws TelegramApiException {
        // If the caption explicitly indicates a conversational query or question rather than logging a receipt
        if (isImageChatIntent(caption)) {
            sendText(chatId, "🤔 *PLOA is analyzing your image...*");
            AssistantService.ChatResult res = assistantService.chatDetailed(String.valueOf(chatId), caption, photoBytes, mimeType);
            sendText(chatId, res.reply());
            return;
        }

        // Otherwise, attempt OCR extraction as a receipt / payment document
        sendText(chatId, "🔍 Analyzing document...");
        String imageSha256 = computeSha256(photoBytes);
        OcrResult ocr = ocrService.extractFromImage(photoBytes, mimeType);

        // Check if the image actually contained a financial transaction
        boolean isFinancialDocument = ocr != null
                && ocr.totalAmount() != null
                && ocr.totalAmount() > 0
                && !"UNKNOWN".equalsIgnoreCase(ocr.documentType());

        if (isFinancialDocument) {
            Transaction tx = buildTransaction(ocr, msgId, imageSha256);
            var dedup = financeService.checkDuplicate(ocr, imageSha256);
            if (dedup.hasDuplicate()) {
                tx.setDuplicateWarning(dedup.warningMessage());
            }

            Transaction saved = financeService.save(tx);
            financeService.recordOcrScan(saved, ocr, imageSha256);

            SendMessage msg = messageFactory.buildConfirmationMessage(chatId, saved, ocr);
            var sent = telegramClient.execute(msg);
            if (sent != null) {
                saved.setTelegramMsgId((long) sent.getMessageId());
                financeService.save(saved);
            }
        } else {
            // Not a financial transaction! User sent an image to chat or consult with the assistant (food, workout, general).
            log.info("Uploaded image is not a financial receipt (document_type={}, amount={}); routing to Assistant vision chat",
                    ocr != null ? ocr.documentType() : null, ocr != null ? ocr.totalAmount() : null);
            String prompt = (caption != null && !caption.isBlank())
                    ? caption
                    : "Please analyze this image in the context of my personal health, nutrition, fitness, or daily life.";
            sendText(chatId, "🤔 *Not a financial receipt. Consulting Life OS Assistant...*");
            AssistantService.ChatResult res = assistantService.chatDetailed(String.valueOf(chatId), prompt, photoBytes, mimeType);
            sendText(chatId, res.reply());
        }
    }

    boolean isImageChatIntent(String caption) {
        if (caption == null || caption.isBlank()) return false;
        String c = caption.trim().toLowerCase();
        if (c.matches("(?i)^/(expense|pay|spend)\\b.*")) {
            return false;
        }
        return c.contains("?") || c.matches("(?i).*(what|how|why|should|can|could|is this|explain|help|calories?|workout|nutrition).*");
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

        // Check if the user is replying to a pending transaction confirmation card
        var replyTo = update.getMessage().getReplyToMessage();
        if (replyTo != null && replyTo.getMessageId() != null) {
            long repliedMsgId = replyTo.getMessageId();
            var pendingTxOpt = financeService.findByTelegramMsgId(repliedMsgId);
            if (pendingTxOpt.isPresent() && !pendingTxOpt.get().isConfirmed()) {
                if (isConfirmationIntent(text)) {
                    confirmAndNotify(chatId, (int) repliedMsgId, pendingTxOpt.get());
                    return;
                }
                handleTransactionRefinement(chatId, (int) repliedMsgId, pendingTxOpt.get(), text);
                return;
            }
        }

        // Conversational confirmation without explicit reply if there is an active pending transaction
        if (isConfirmationIntent(text)) {
            var pendingList = financeService.getPendingTransactions();
            if (!pendingList.isEmpty()) {
                Transaction latestPending = pendingList.get(0);
                int cardMsgId = latestPending.getTelegramMsgId() != null ? latestPending.getTelegramMsgId().intValue() : 0;
                confirmAndNotify(chatId, cardMsgId, latestPending);
                return;
            }
        }

        // All natural conversational text routes to AssistantService (Option 1: Multilingual Agent)
        sendText(chatId, "🤔 *PLOA is thinking...*");
        AssistantService.ChatResult result = assistantService.chatDetailed(String.valueOf(chatId), text);
        sendText(chatId, result.reply());

        if (result.recordedTransaction() != null) {
            Transaction saved = result.recordedTransaction();
            SendMessage msg = messageFactory.buildConfirmationMessage(chatId, saved, result.ocrResult());
            var sent = telegramClient.execute(msg);
            if (sent != null) {
                saved.setTelegramMsgId((long) sent.getMessageId());
                financeService.save(saved);
            }
        }
    }

    void processExpenseText(long chatId, String text, boolean isExplicitCommand) throws TelegramApiException {
        OcrResult ocr = ocrService.extractFromText(text);

        if (ocr.totalAmount() == null || ocr.totalAmount() <= 0) {
            if (isExplicitCommand) {
                sendText(chatId, "⚠️ Could not detect a valid expense amount from: `" + text + "`. Example: `/expense 50k coffee`");
            } else {
                log.info("Text OCR returned non-positive amount ({}) for text '{}'; routing to assistant chat",
                        ocr.totalAmount(), text);
                sendText(chatId, "🤔 *PLOA is thinking...*");
                String reply = assistantService.chat(String.valueOf(chatId), text);
                sendText(chatId, reply);
            }
            return;
        }

        Transaction tx = buildTransaction(ocr, null, null);

        var dedup = financeService.checkDuplicate(ocr, null);
        if (dedup.hasDuplicate()) {
            tx.setDuplicateWarning(dedup.warningMessage());
        }

        Transaction saved = financeService.save(tx);
        financeService.recordOcrScan(saved, ocr, null);

        SendMessage msg = messageFactory.buildConfirmationMessage(chatId, saved, ocr);
        var sent = telegramClient.execute(msg);
        if (sent != null) {
            saved.setTelegramMsgId((long) sent.getMessageId());
            financeService.save(saved);
        }
    }

    // ── Command handler ───────────────────────────────────────────────────────

    private void handleCommand(long chatId, String command) throws TelegramApiException {
        String[] parts = command.trim().split("\\s+", 2);
        String baseCmd = parts[0].split("@")[0].toLowerCase();
        String args = parts.length > 1 ? parts[1].trim() : "";

        switch (baseCmd) {
            case "/start", "/help" -> sendText(chatId, messageFactory.helpText());
            case "/new", "/reset" -> {
                assistantService.clearChatHistory(String.valueOf(chatId));
                sendText(chatId, "🧹 *Conversation reset!* Started a fresh chat session (long-term goals and profile preferences remain preserved).");
            }
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
                sendText(chatId, "⏳ *Synthesizing daily morning briefing with AI...*");
                String briefing = briefingOrchestrator.generateBriefingPreview();
                sendText(chatId, briefing);
            }
            case "/evening", "/recap" -> {
                sendText(chatId, "⏳ *Synthesizing daily evening summary with AI...*");
                String evening = briefingOrchestrator.generateEveningSummaryPreview();
                sendText(chatId, evening);
            }
            case "/sync" -> {
                sendText(chatId, "⏳ *Pulling live data from Garmin Connect...*");
                try {
                    healthService.triggerGarminPull();
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
            case "/expense", "/spend", "/pay" -> {
                if (args.isBlank()) {
                    sendText(chatId, "⚠️ Please specify an amount and description. Example: `/expense 50k coffee`");
                } else {
                    processExpenseText(chatId, args, true);
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

        // Immediately acknowledge callback query to avoid button spinning/freezing in Telegram client
        try {
            telegramClient.execute(AnswerCallbackQuery.builder()
                    .callbackQueryId(cb.getId())
                    .build());
        } catch (Exception e) {
            log.debug("Failed to answer callback query: {}", e.getMessage());
        }

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
            case "EDIT" -> {
                Optional<Transaction> txOpt = financeService.findById(txId);
                if (txOpt.isPresent()) {
                    Transaction tx = txOpt.get();
                    SendMessage card = messageFactory.buildConfirmationMessage(chatId, tx, null);
                    String editPrompt = card.getText() + "\n\n✏️ <b>Edit Mode:</b> Swipe & reply directly to this card with your correction (e.g. <i>'amount 45k'</i>, <i>'category Food'</i>), or tap <b>[✅ Confirm]</b> when ready.";
                    telegramClient.execute(EditMessageText.builder()
                            .chatId(chatId)
                            .messageId(msgId)
                            .text(editPrompt)
                            .parseMode("HTML")
                            .replyMarkup((InlineKeyboardMarkup) card.getReplyMarkup())
                            .build());
                }
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

    private void handleTransactionRefinement(long chatId, int cardMsgId, Transaction tx, String instruction) throws TelegramApiException {
        sendText(chatId, "✏️ *Updating transaction details...*");
        try {
            OcrResult refined = ocrService.refineFromUserInstruction(tx, instruction);
            Transaction updated = financeService.applyCorrection(tx.getId(), refined, instruction);

            // Update old card message to indicate it has been revised
            try {
                editText(chatId, cardMsgId, "🔄 <i>Draft updated below ⬇️</i>");
            } catch (Exception ignored) {}

            // Send fresh confirmation card with all interactive buttons AT THE BOTTOM of the chat
            SendMessage newCard = messageFactory.buildConfirmationMessage(chatId, updated, refined);
            var sent = telegramClient.execute(newCard);
            if (sent != null) {
                updated.setTelegramMsgId((long) sent.getMessageId());
                financeService.save(updated);
            }
        } catch (Exception e) {
            log.error("Failed to refine transaction: {}", e.getMessage(), e);
            sendText(chatId, "❌ Could not update transaction: " + e.getMessage());
        }
    }

    private void confirmAndNotify(long chatId, int cardMsgId, Transaction tx) throws TelegramApiException {
        financeService.confirm(tx.getId());
        if (cardMsgId > 0) {
            try {
                editText(chatId, cardMsgId, "✅ <b>Transaction Confirmed!</b>\nAmount: " + formatVnd(tx.getAmount()) + " VND (" + tx.getCategory() + ")");
            } catch (Exception ignored) {}
        }
        sendText(chatId, "✅ *Transaction confirmed!* " + formatVnd(tx.getAmount()) + " VND for *" + (tx.getMerchant() != null ? tx.getMerchant() : tx.getCategory()) + "* has been locked into your ledger.");
    }

    private boolean isConfirmationIntent(String text) {
        if (text == null) return false;
        String t = text.trim().toLowerCase();
        return t.matches("^(confirm|yes|ok|okay|done|save|correct|accurate|proceed|looks good|accept)$");
    }

    private String formatVnd(Long amount) {
        if (amount == null) return "0";
        return java.text.NumberFormat.getInstance(new java.util.Locale("vi", "VN")).format(amount);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Transaction buildTransaction(OcrResult ocr, Long telegramMsgId, String imageSha256) {
        Transaction tx = new Transaction();
        LocalDate d = ocr.transactionDate() != null ? ocr.transactionDate() : LocalDate.now();
        tx.setTxDate(d);

        // Precise temporal modeling: use bill time if present, fallback to upload time
        if (ocr.transactionTime() != null && !ocr.transactionTime().isBlank()) {
            try {
                String timeStr = ocr.transactionTime().trim();
                java.time.LocalTime t = java.time.LocalTime.parse(timeStr.length() == 5 ? timeStr + ":00" : timeStr);
                tx.setTxTimestamp(d.atTime(t).atZone(java.time.ZoneId.of("Asia/Ho_Chi_Minh")).toOffsetDateTime());
            } catch (Exception e) {
                tx.setTxTimestamp(java.time.OffsetDateTime.now());
            }
        } else {
            tx.setTxTimestamp(java.time.OffsetDateTime.now());
        }

        tx.setImageSha256(imageSha256);
        tx.setInvoiceNumber(ocr.invoiceNumber());
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

    private String computeSha256(byte[] data) {
        if (data == null || data.length == 0) return null;
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(data);
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }

    boolean isAllowed(long chatId) {
        List<Long> allowed = properties.telegram().allowedChatIdList();
        return !allowed.isEmpty() && allowed.contains(chatId);
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
