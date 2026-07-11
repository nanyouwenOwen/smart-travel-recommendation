CREATE TABLE location_references (
 id CHAR(36) NOT NULL, provider VARCHAR(40) NOT NULL, provider_ref VARCHAR(160) NOT NULL,
 name VARCHAR(200) NOT NULL, display_name VARCHAR(500) NOT NULL, country_code CHAR(2) NULL,
 latitude DECIMAL(10,7) NOT NULL, longitude DECIMAL(10,7) NOT NULL, timezone VARCHAR(64) NOT NULL,
 type VARCHAR(32) NOT NULL, source_updated_at TIMESTAMP(6) NULL, fetched_at TIMESTAMP(6) NOT NULL,
 expires_at TIMESTAMP(6) NOT NULL, created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
 PRIMARY KEY(id), UNIQUE KEY uk_location_provider_ref(provider,provider_ref), KEY idx_location_expiry(expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE external_data_cache (
 id CHAR(36) NOT NULL, cache_key CHAR(64) NOT NULL, provider VARCHAR(40) NOT NULL,
 data_type VARCHAR(32) NOT NULL, payload JSON NOT NULL, source_updated_at TIMESTAMP(6) NULL,
 fetched_at TIMESTAMP(6) NOT NULL, expires_at TIMESTAMP(6) NOT NULL, stale_until TIMESTAMP(6) NOT NULL,
 last_failure_code VARCHAR(64) NULL, created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
 PRIMARY KEY(id), UNIQUE KEY uk_external_cache_key(cache_key), KEY idx_external_cache_cleanup(stale_until)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

ALTER TABLE trips ADD COLUMN destination_location_id CHAR(36) NULL,
 ADD CONSTRAINT fk_trip_destination_location FOREIGN KEY(destination_location_id) REFERENCES location_references(id);

ALTER TABLE messages ADD COLUMN source_references JSON NULL, ADD COLUMN data_updated_at TIMESTAMP(6) NULL;
