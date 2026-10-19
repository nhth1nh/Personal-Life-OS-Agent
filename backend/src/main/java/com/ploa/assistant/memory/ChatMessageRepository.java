package com.ploa.assistant.memory;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, UUID> {

    List<ChatMessage> findByChatIdOrderByCreatedAtDesc(String chatId, Pageable pageable);

    void deleteByChatId(String chatId);
}
