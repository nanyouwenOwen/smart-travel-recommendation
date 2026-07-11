CREATE TABLE users (
    id CHAR(36) NOT NULL,
    email VARCHAR(254) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    display_name VARCHAR(50) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    deleted_at TIMESTAMP(6) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_users_email (email),
    KEY idx_users_deleted_at (deleted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE trips (
    id CHAR(36) NOT NULL,
    user_id CHAR(36) NOT NULL,
    destination VARCHAR(200) NOT NULL,
    start_date DATE NOT NULL,
    days TINYINT UNSIGNED NOT NULL,
    travelers SMALLINT UNSIGNED NOT NULL,
    budget_amount DECIMAL(14, 2) NOT NULL,
    currency CHAR(3) NOT NULL,
    timezone VARCHAR(64) NOT NULL,
    preferences_json JSON NOT NULL,
    additional_requirements TEXT NULL,
    status VARCHAR(20) NOT NULL,
    current_version_id CHAR(36) NULL,
    failure_code VARCHAR(64) NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    deleted_at TIMESTAMP(6) NULL,
    PRIMARY KEY (id),
    KEY idx_trips_user_created (user_id, created_at DESC),
    KEY idx_trips_current_version (current_version_id),
    CONSTRAINT fk_trips_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT chk_trips_days CHECK (days BETWEEN 1 AND 30),
    CONSTRAINT chk_trips_travelers CHECK (travelers BETWEEN 1 AND 50),
    CONSTRAINT chk_trips_budget CHECK (budget_amount > 0),
    CONSTRAINT chk_trips_status CHECK (status IN ('DRAFT', 'GENERATING', 'READY', 'FAILED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE trip_versions (
    id CHAR(36) NOT NULL,
    trip_id CHAR(36) NOT NULL,
    version_number INT UNSIGNED NOT NULL,
    adjustment_instruction TEXT NULL,
    estimated_total DECIMAL(14, 2) NULL,
    currency CHAR(3) NOT NULL,
    warnings_json JSON NOT NULL,
    source_updated_at TIMESTAMP(6) NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_trip_versions_number (trip_id, version_number),
    CONSTRAINT fk_trip_versions_trip FOREIGN KEY (trip_id) REFERENCES trips (id) ON DELETE CASCADE,
    CONSTRAINT chk_trip_versions_total CHECK (estimated_total IS NULL OR estimated_total >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

ALTER TABLE trips
    ADD CONSTRAINT fk_trips_current_version
    FOREIGN KEY (current_version_id) REFERENCES trip_versions (id);

CREATE TABLE itinerary_days (
    id CHAR(36) NOT NULL,
    trip_version_id CHAR(36) NOT NULL,
    day_number TINYINT UNSIGNED NOT NULL,
    itinerary_date DATE NOT NULL,
    summary VARCHAR(500) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_itinerary_days_number (trip_version_id, day_number),
    CONSTRAINT fk_itinerary_days_version FOREIGN KEY (trip_version_id)
        REFERENCES trip_versions (id) ON DELETE CASCADE,
    CONSTRAINT chk_itinerary_days_number CHECK (day_number BETWEEN 1 AND 30)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE activities (
    id CHAR(36) NOT NULL,
    itinerary_day_id CHAR(36) NOT NULL,
    sequence_number SMALLINT UNSIGNED NOT NULL,
    start_time TIME NOT NULL,
    end_time TIME NOT NULL,
    title VARCHAR(200) NOT NULL,
    location VARCHAR(300) NOT NULL,
    description TEXT NULL,
    estimated_cost DECIMAL(14, 2) NOT NULL DEFAULT 0,
    currency CHAR(3) NOT NULL,
    transport_advice TEXT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_activities_sequence (itinerary_day_id, sequence_number),
    CONSTRAINT fk_activities_day FOREIGN KEY (itinerary_day_id)
        REFERENCES itinerary_days (id) ON DELETE CASCADE,
    CONSTRAINT chk_activities_time CHECK (end_time > start_time),
    CONSTRAINT chk_activities_cost CHECK (estimated_cost >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE conversations (
    id CHAR(36) NOT NULL,
    user_id CHAR(36) NOT NULL,
    trip_id CHAR(36) NULL,
    title VARCHAR(100) NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    deleted_at TIMESTAMP(6) NULL,
    PRIMARY KEY (id),
    KEY idx_conversations_user_updated (user_id, updated_at DESC),
    KEY idx_conversations_trip (trip_id),
    CONSTRAINT fk_conversations_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_conversations_trip FOREIGN KEY (trip_id) REFERENCES trips (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE messages (
    id CHAR(36) NOT NULL,
    conversation_id CHAR(36) NOT NULL,
    role VARCHAR(16) NOT NULL,
    content MEDIUMTEXT NOT NULL,
    status VARCHAR(16) NOT NULL,
    model VARCHAR(100) NULL,
    input_tokens INT UNSIGNED NULL,
    output_tokens INT UNSIGNED NULL,
    error_code VARCHAR(64) NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    completed_at TIMESTAMP(6) NULL,
    PRIMARY KEY (id),
    KEY idx_messages_conversation_created (conversation_id, created_at),
    CONSTRAINT fk_messages_conversation FOREIGN KEY (conversation_id)
        REFERENCES conversations (id) ON DELETE CASCADE,
    CONSTRAINT chk_messages_role CHECK (role IN ('USER', 'ASSISTANT', 'SYSTEM')),
    CONSTRAINT chk_messages_status CHECK (status IN ('PENDING', 'STREAMING', 'COMPLETED', 'FAILED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

