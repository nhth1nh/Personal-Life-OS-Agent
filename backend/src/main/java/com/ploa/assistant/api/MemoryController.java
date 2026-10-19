package com.ploa.assistant.api;

import com.ploa.assistant.memory.MemoryService;
import com.ploa.assistant.memory.UserMemory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/memory")
public class MemoryController {

    private final MemoryService memoryService;

    public MemoryController(MemoryService memoryService) {
        this.memoryService = memoryService;
    }

    public record MemoryRequest(
            String category,
            String key,
            String value
    ) {}

    @GetMapping
    public ResponseEntity<List<UserMemory>> getAllMemories(
            @RequestParam(required = false) String category) {
        if (category != null && !category.isBlank()) {
            return ResponseEntity.ok(memoryService.getPreferencesByCategory(category));
        }
        return ResponseEntity.ok(memoryService.getAllPreferences());
    }

    @PostMapping
    public ResponseEntity<UserMemory> saveMemory(@RequestBody MemoryRequest request) {
        UserMemory saved = memoryService.savePreference(
                request.category(),
                request.key(),
                request.value(),
                "USER_EXPLICIT"
        );
        return ResponseEntity.ok(saved);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteMemory(@PathVariable UUID id) {
        memoryService.deletePreference(id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/history")
    public ResponseEntity<Void> clearHistory(@RequestParam String chatId) {
        memoryService.clearChatHistory(chatId);
        return ResponseEntity.noContent().build();
    }
}
