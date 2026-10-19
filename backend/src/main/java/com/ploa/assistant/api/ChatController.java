package com.ploa.assistant.api;

import com.ploa.assistant.AssistantService;
import com.ploa.assistant.dto.ChatRequest;
import com.ploa.assistant.dto.ChatResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/chat")
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
        String reply = assistantService.chat(request.message());
        return ResponseEntity.ok(new ChatResponse(reply, "CHAT"));
    }

    /**
     * Browser-friendly GET endpoint: /api/v1/chat?q=...&apiKey=...
     */
    @GetMapping
    public ResponseEntity<ChatResponse> chatGet(
            @RequestParam(name = "q", required = false) String q,
            @RequestParam(name = "message", required = false) String message) {
        String query = q != null ? q : message;
        if (query == null || query.isBlank()) {
            query = "Tổng quan tình hình sức khỏe và tài chính hôm nay của tôi thế nào?";
        }
        String reply = assistantService.chat(query);
        return ResponseEntity.ok(new ChatResponse(reply, "CHAT"));
    }
}
