package com.ploa.assistant.memory;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_memories", uniqueConstraints = {
        @UniqueConstraint(name = "uq_user_memory_key", columnNames = {"category", "memory_key"})
})
public class UserMemory {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(nullable = false, length = 32)
    private String category; // "FITNESS", "DIET", "FINANCE", "LIFESTYLE", "GOAL", "GENERAL"

    @Column(name = "memory_key", nullable = false, length = 128)
    private String memoryKey;

    @Column(name = "memory_value", nullable = false, columnDefinition = "TEXT")
    private String memoryValue;

    @Column(nullable = false, length = 32)
    private String source = "CONVERSATION"; // "CONVERSATION", "USER_EXPLICIT"

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public UserMemory() {
    }

    public UserMemory(String category, String memoryKey, String memoryValue, String source) {
        this.id = UUID.randomUUID();
        this.category = category;
        this.memoryKey = memoryKey;
        this.memoryValue = memoryValue;
        this.source = source != null ? source : "CONVERSATION";
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getMemoryKey() {
        return memoryKey;
    }

    public void setMemoryKey(String memoryKey) {
        this.memoryKey = memoryKey;
    }

    public String getMemoryValue() {
        return memoryValue;
    }

    public void setMemoryValue(String memoryValue) {
        this.memoryValue = memoryValue;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
