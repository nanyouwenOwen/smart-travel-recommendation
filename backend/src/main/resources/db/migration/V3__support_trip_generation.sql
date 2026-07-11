ALTER TABLE activities
    ADD COLUMN budget_category VARCHAR(32) NOT NULL DEFAULT 'OTHER' AFTER currency,
    ADD CONSTRAINT chk_activities_budget_category CHECK (
        budget_category IN ('TRANSPORTATION','ACCOMMODATION','FOOD','ATTRACTION','SHOPPING','OTHER'));

ALTER TABLE trip_versions
    ADD COLUMN generation_type VARCHAR(16) NOT NULL DEFAULT 'INITIAL' AFTER version_number,
    ADD COLUMN prompt_version VARCHAR(64) NOT NULL DEFAULT 'trip-planner/v1' AFTER generation_type,
    ADD COLUMN provider VARCHAR(64) NOT NULL DEFAULT 'stub' AFTER prompt_version,
    ADD COLUMN model VARCHAR(100) NOT NULL DEFAULT 'stub-v1' AFTER provider,
    ADD COLUMN input_tokens INT UNSIGNED NULL,
    ADD COLUMN output_tokens INT UNSIGNED NULL,
    ADD COLUMN generation_duration_ms BIGINT UNSIGNED NULL,
    ADD CONSTRAINT chk_trip_versions_generation_type CHECK (
        generation_type IN ('INITIAL','REPLAN','ADJUSTMENT'));

CREATE TABLE trip_generation_jobs (
    id CHAR(36) NOT NULL,
    trip_id CHAR(36) NOT NULL,
    base_version_id CHAR(36) NULL,
    generation_type VARCHAR(16) NOT NULL,
    status VARCHAR(16) NOT NULL,
    adjustment_instruction TEXT NULL,
    request_snapshot JSON NOT NULL,
    attempt INT UNSIGNED NOT NULL DEFAULT 0,
    failure_code VARCHAR(64) NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    started_at TIMESTAMP(6) NULL,
    completed_at TIMESTAMP(6) NULL,
    PRIMARY KEY (id),
    KEY idx_trip_jobs_trip_created (trip_id, created_at DESC),
    KEY idx_trip_jobs_status_created (status, created_at),
    CONSTRAINT fk_trip_jobs_trip FOREIGN KEY (trip_id) REFERENCES trips (id) ON DELETE CASCADE,
    CONSTRAINT fk_trip_jobs_base_version FOREIGN KEY (base_version_id) REFERENCES trip_versions (id),
    CONSTRAINT chk_trip_jobs_type CHECK (generation_type IN ('INITIAL','REPLAN','ADJUSTMENT')),
    CONSTRAINT chk_trip_jobs_status CHECK (status IN ('QUEUED','RUNNING','SUCCEEDED','FAILED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE idempotency_records (
    id CHAR(36) NOT NULL,
    user_id CHAR(36) NOT NULL,
    operation VARCHAR(64) NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    request_hash CHAR(64) NOT NULL,
    resource_id CHAR(36) NOT NULL,
    expires_at TIMESTAMP(6) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_idempotency_user_operation_key (user_id, operation, idempotency_key),
    KEY idx_idempotency_expires (expires_at),
    CONSTRAINT fk_idempotency_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE trip_version_restores (
    id CHAR(36) NOT NULL,
    trip_id CHAR(36) NOT NULL,
    from_version_id CHAR(36) NULL,
    to_version_id CHAR(36) NOT NULL,
    user_id CHAR(36) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY idx_trip_restores_trip_created (trip_id, created_at DESC),
    CONSTRAINT fk_trip_restores_trip FOREIGN KEY (trip_id) REFERENCES trips (id) ON DELETE CASCADE,
    CONSTRAINT fk_trip_restores_from FOREIGN KEY (from_version_id) REFERENCES trip_versions (id),
    CONSTRAINT fk_trip_restores_to FOREIGN KEY (to_version_id) REFERENCES trip_versions (id),
    CONSTRAINT fk_trip_restores_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE INDEX idx_trips_owner_lifecycle ON trips (user_id, deleted_at, created_at DESC, id DESC);
