CREATE TABLE site_scan (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id    BIGINT       NOT NULL,
    scan_type     VARCHAR(20)  NOT NULL,
    status        VARCHAR(20)  NOT NULL,
    started_at    DATETIME(6)  NOT NULL,
    finished_at   DATETIME(6)  NULL,
    error_message VARCHAR(255) NULL,
    raw_json      MEDIUMTEXT   NULL,
    CONSTRAINT fk_site_scan_project FOREIGN KEY (project_id) REFERENCES project (id) ON DELETE CASCADE
);

-- 점검 시작을 한 줄로 세우는 잠금용 행이다.
CREATE TABLE site_scan_lock (
    id TINYINT PRIMARY KEY
);

INSERT INTO site_scan_lock (id) VALUES (1);

CREATE TABLE site_finding (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id     BIGINT       NOT NULL,
    alert_ref      VARCHAR(50)  NOT NULL,
    name           VARCHAR(255) NOT NULL,
    risk           VARCHAR(20)  NOT NULL,
    description    TEXT         NOT NULL,
    solution       TEXT         NOT NULL,
    reference      TEXT         NOT NULL,
    cwe_id         INT          NULL,
    instance_count INT          NOT NULL,
    sample_urls    TEXT         NOT NULL,
    status         VARCHAR(20)  NOT NULL DEFAULT 'NEW',
    first_seen_at  DATETIME(6)  NOT NULL,
    last_seen_at   DATETIME(6)  NOT NULL,
    CONSTRAINT uk_site_finding UNIQUE (project_id, alert_ref),
    CONSTRAINT fk_site_finding_project FOREIGN KEY (project_id) REFERENCES project (id) ON DELETE CASCADE
);

CREATE TABLE site_finding_status_history (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    site_finding_id BIGINT      NOT NULL,
    from_status     VARCHAR(20) NOT NULL,
    to_status       VARCHAR(20) NOT NULL,
    changed_at      DATETIME(6) NOT NULL,
    CONSTRAINT fk_site_status_history_finding FOREIGN KEY (site_finding_id) REFERENCES site_finding (id) ON DELETE CASCADE
);
