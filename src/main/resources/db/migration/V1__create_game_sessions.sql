CREATE TABLE game_sessions (
    id VARCHAR(36) PRIMARY KEY,
    epoch VARCHAR(36) NOT NULL,
    snapshot TEXT NOT NULL,
    revision BIGINT NOT NULL DEFAULT 0,
    last_request_id VARCHAR(36)
);
