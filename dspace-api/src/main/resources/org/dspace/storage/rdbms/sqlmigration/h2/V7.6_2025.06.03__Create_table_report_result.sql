CREATE TABLE report_result (
    report_result_id INTEGER AUTO_INCREMENT PRIMARY KEY,
    type VARCHAR(256),
    value TEXT,
    executor_id UUID,
    args TEXT,
    last_modified TIMESTAMP,
    FOREIGN KEY (executor_id) REFERENCES EPerson(uuid) ON DELETE SET NULL
);
