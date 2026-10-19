package com.ploa.assistant.memory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Service
public class MemoryService {

    private static final Logger log = LoggerFactory.getLogger(MemoryService.class);

    private final ChatMessageRepository chatRepo;
    private final UserMemoryRepository memoryRepo;

    public MemoryService(ChatMessageRepository chatRepo, UserMemoryRepository memoryRepo) {
        this.chatRepo = chatRepo;
        this.memoryRepo = memoryRepo;
    }

    // ── Chat Memory (Short-Term Multi-turn) ───────────────────────────────────

    @Transactional
    public void saveChatMessage(String chatId, String role, String content) {
        if (chatId == null || content == null || content.isBlank()) {
            return;
        }
        ChatMessage msg = new ChatMessage(chatId, role, content.trim());
        chatRepo.save(msg);
    }

    @Transactional(readOnly = true)
    public List<ChatMessage> getRecentChatHistory(String chatId, int limit) {
        if (chatId == null) {
            return List.of();
        }
        int l = limit > 0 ? limit : 8;
        List<ChatMessage> desc = chatRepo.findByChatIdOrderByCreatedAtDesc(chatId, PageRequest.of(0, l));
        // Reverse so it's in chronological order (oldest to newest)
        List<ChatMessage> chronological = new java.util.ArrayList<>(desc);
        Collections.reverse(chronological);
        return chronological;
    }

    @Transactional
    public void clearChatHistory(String chatId) {
        chatRepo.deleteByChatId(chatId);
    }

    // ── User Memories & Preferences (Long-Term) ───────────────────────────────

    @Transactional
    public UserMemory savePreference(String category, String key, String value, String source) {
        String cat = (category != null && !category.isBlank()) ? category.toUpperCase().trim() : "GENERAL";
        String k = key.trim();
        String v = value.trim();

        return memoryRepo.findByCategoryIgnoreCaseAndMemoryKeyIgnoreCase(cat, k)
                .map(existing -> {
                    existing.setMemoryValue(v);
                    if (source != null) existing.setSource(source);
                    log.info("Updated user memory [{} / {}]: {}", cat, k, v);
                    return memoryRepo.save(existing);
                })
                .orElseGet(() -> {
                    UserMemory m = new UserMemory(cat, k, v, source);
                    log.info("Created user memory [{} / {}]: {}", cat, k, v);
                    return memoryRepo.save(m);
                });
    }

    @Transactional(readOnly = true)
    public List<UserMemory> getAllPreferences() {
        return memoryRepo.findAllByOrderByCategoryAscMemoryKeyAsc();
    }

    @Transactional(readOnly = true)
    public List<UserMemory> getPreferencesByCategory(String category) {
        return memoryRepo.findByCategoryIgnoreCase(category);
    }

    @Transactional
    public void deletePreference(UUID id) {
        memoryRepo.deleteById(id);
        log.info("Deleted user memory ID: {}", id);
    }
}
