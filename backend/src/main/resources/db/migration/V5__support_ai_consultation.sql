ALTER TABLE messages
    ADD COLUMN turn_id CHAR(36) NULL AFTER conversation_id,
    ADD COLUMN trip_version_id CHAR(36) NULL AFTER turn_id,
    ADD CONSTRAINT fk_messages_trip_version FOREIGN KEY (trip_version_id) REFERENCES trip_versions(id);

CREATE TABLE conversation_message_requests (
    id CHAR(36) NOT NULL,
    conversation_id CHAR(36) NOT NULL,
    operation VARCHAR(32) NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    request_hash CHAR(64) NOT NULL,
    user_message_id CHAR(36) NOT NULL,
    assistant_message_id CHAR(36) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_message_request (conversation_id, operation, idempotency_key),
    CONSTRAINT fk_message_requests_conversation FOREIGN KEY (conversation_id) REFERENCES conversations(id) ON DELETE CASCADE,
    CONSTRAINT fk_message_requests_user_message FOREIGN KEY (user_message_id) REFERENCES messages(id),
    CONSTRAINT fk_message_requests_assistant_message FOREIGN KEY (assistant_message_id) REFERENCES messages(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE conversation_streams (
    id CHAR(36) NOT NULL,
    conversation_id CHAR(36) NOT NULL,
    assistant_message_id CHAR(36) NOT NULL,
    status VARCHAR(16) NOT NULL,
    last_event_sequence INT UNSIGNED NOT NULL DEFAULT 0,
    expires_at TIMESTAMP(6) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    completed_at TIMESTAMP(6) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_stream_assistant_message (assistant_message_id),
    KEY idx_streams_expiry (expires_at),
    CONSTRAINT fk_streams_conversation FOREIGN KEY (conversation_id) REFERENCES conversations(id) ON DELETE CASCADE,
    CONSTRAINT fk_streams_assistant_message FOREIGN KEY (assistant_message_id) REFERENCES messages(id),
    CONSTRAINT chk_streams_status CHECK (status IN ('ACTIVE','DISCONNECTED','COMPLETED','FAILED','CANCELLED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE conversation_stream_events (
    id CHAR(36) NOT NULL,
    stream_id CHAR(36) NOT NULL,
    sequence_number INT UNSIGNED NOT NULL,
    event_type VARCHAR(16) NOT NULL,
    payload_json JSON NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    expires_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_stream_event_sequence (stream_id, sequence_number),
    KEY idx_stream_events_expiry (expires_at),
    CONSTRAINT fk_stream_events_stream FOREIGN KEY (stream_id) REFERENCES conversation_streams(id) ON DELETE CASCADE,
    CONSTRAINT chk_stream_events_type CHECK (event_type IN ('ACK','DELTA','DONE','ERROR'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE INDEX idx_conversations_owner_lifecycle ON conversations(user_id, deleted_at, updated_at DESC, id DESC);
