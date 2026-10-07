CREATE TABLE store_ai_reports (
    store_id BIGINT PRIMARY KEY REFERENCES stores(id),
    attempted_at TIMESTAMP WITH TIME ZONE NOT NULL,
    generated_at TIMESTAMP WITH TIME ZONE,
    model VARCHAR(120),
    report_json TEXT,
    evidence_json TEXT,
    error_message VARCHAR(255)
);
