ALTER TABLE project
    ADD COLUMN last_checked_at  DATETIME(6)  NULL,
    ADD COLUMN last_check_error VARCHAR(255) NULL;

CREATE TABLE finding (
    id                       BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id               BIGINT        NOT NULL,
    dependency_id            BIGINT        NOT NULL,
    primary_vulnerability_id VARCHAR(100)  NOT NULL,
    summary                  VARCHAR(1000) NOT NULL,
    severity                 VARCHAR(20)   NOT NULL,
    fixed_versions           VARCHAR(1000) NOT NULL DEFAULT '',
    reference_url            VARCHAR(500)  NOT NULL,
    raw_json                 MEDIUMTEXT    NOT NULL,
    status                   VARCHAR(20)   NOT NULL DEFAULT 'NEW',
    first_seen_at            DATETIME(6)   NOT NULL,
    CONSTRAINT fk_finding_project FOREIGN KEY (project_id) REFERENCES project (id) ON DELETE CASCADE,
    CONSTRAINT fk_finding_dependency FOREIGN KEY (dependency_id) REFERENCES dependency (id) ON DELETE CASCADE
);

CREATE TABLE finding_key (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    finding_id        BIGINT       NOT NULL,
    dependency_id     BIGINT       NOT NULL,
    vulnerability_key VARCHAR(150) NOT NULL,
    CONSTRAINT uk_finding_key UNIQUE (dependency_id, vulnerability_key),
    CONSTRAINT fk_finding_key_finding FOREIGN KEY (finding_id) REFERENCES finding (id) ON DELETE CASCADE,
    CONSTRAINT fk_finding_key_dependency FOREIGN KEY (dependency_id) REFERENCES dependency (id) ON DELETE CASCADE
);
