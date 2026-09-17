CREATE TABLE outbox_events (
    id              RAW(16) PRIMARY KEY,
    aggregate_id    RAW(16) NOT NULL,
    event_type      VARCHAR2(100) NOT NULL,
    payload         JSON NOT NULL,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    published_at    TIMESTAMP NULL
);

CREATE INDEX idx_outbox_events_aggregate_id ON outbox_events(aggregate_id);
CREATE INDEX idx_outbox_events_unpublished ON outbox_events (CASE WHEN published_at IS NULL THEN created_at END);
