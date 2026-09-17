CREATE TABLE processed_events (
    event_id     RAW(16) PRIMARY KEY,
    processed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL
);
