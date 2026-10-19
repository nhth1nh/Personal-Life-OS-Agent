-- V3: Multi-turn chat memory and long-term user preferences/profile

CREATE TABLE IF NOT EXISTS chat_messages (
    id UUID PRIMARY KEY,
    chat_id VARCHAR(64) NOT NULL,
    role VARCHAR(20) NOT NULL,
    content TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_chat_messages_chat_id_created 
    ON chat_messages (chat_id, created_at DESC);

CREATE TABLE IF NOT EXISTS user_memories (
    id UUID PRIMARY KEY,
    category VARCHAR(32) NOT NULL,
    memory_key VARCHAR(128) NOT NULL,
    memory_value TEXT NOT NULL,
    source VARCHAR(32) NOT NULL DEFAULT 'CONVERSATION',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_user_memory_key UNIQUE (category, memory_key)
);

CREATE INDEX IF NOT EXISTS idx_user_memories_category 
    ON user_memories (category);
