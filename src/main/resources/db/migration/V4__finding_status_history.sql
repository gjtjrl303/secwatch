CREATE TABLE finding_status_history (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    finding_id  BIGINT      NOT NULL,
    from_status VARCHAR(20) NOT NULL,
    to_status   VARCHAR(20) NOT NULL,
    changed_at  DATETIME(6) NOT NULL,
    CONSTRAINT fk_status_history_finding FOREIGN KEY (finding_id) REFERENCES finding (id) ON DELETE CASCADE
);
