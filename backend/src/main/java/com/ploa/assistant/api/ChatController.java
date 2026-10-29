package com.ploa.assistant.api;

import com.ploa.assistant.AssistantService;
import com.ploa.assistant.dto.ChatRequest;
import com.ploa.assistant.dto.ChatResponse;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping({"/api/v1/chat", "/api/v1/assistant/chat"})
public class ChatController {

    private final AssistantService assistantService;

    public ChatController(AssistantService assistantService) {
        this.assistantService = assistantService;
    }

    /**
     * Ask the Life OS AI Assistant anything about your health, recovery, or finances.
     */
    @PostMapping
    public ResponseEntity<ChatResponse> chat(@Valid @RequestBody ChatRequest request) {
        String chatId = (request.chatId() != null && !request.chatId().isBlank())
                ? request.chatId()
                : "api-session";
        String reply = assistantService.chat(chatId, request.message());
        return ResponseEntity.ok(new ChatResponse(reply, "CHAT"));
    }

    /**
     * Multimodal chat endpoint supporting attached images (receipts, charts, fitness photos).
     */
    @PostMapping(value = "/multimodal", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ChatResponse> chatMultimodal(
            @RequestParam(value = "message", required = false, defaultValue = "") String message,
            @RequestParam(value = "file", required = false) MultipartFile file,
            @RequestParam(value = "chatId", required = false, defaultValue = "api-session") String chatId) {
        byte[] imageBytes = null;
        String mimeType = null;
        if (file != null && !file.isEmpty()) {
            try {
                imageBytes = file.getBytes();
                mimeType = file.getContentType();
            } catch (IOException e) {
                return ResponseEntity.badRequest().body(new ChatResponse("Failed to read attached file: " + e.getMessage(), "ERROR"));
            }
        }
        var result = assistantService.chatDetailed(chatId, message, imageBytes, mimeType);
        String intent = result.recordedTransaction() != null ? "EXPENSE" : "CHAT";
        return ResponseEntity.ok(new ChatResponse(result.reply(), intent));
    }

    /**
     * Browser-friendly GET endpoint: /api/v1/chat?q=...&chatId=...
     * Note: Requires valid authentication header (Authorization: Bearer or X-API-Key).
     */
    @GetMapping
    public ResponseEntity<ChatResponse> chatGet(
            @RequestParam(name = "q", required = false) String q,
            @RequestParam(name = "message", required = false) String message,
            @RequestParam(name = "chatId", defaultValue = "browser-session") String chatId) {
        String query = q != null ? q : message;
        if (query == null || query.isBlank()) {
            query = "What is the summary of my health and financial status today?";
        }
        String reply = assistantService.chat(chatId, query);
        return ResponseEntity.ok(new ChatResponse(reply, "CHAT"));
    }
}
