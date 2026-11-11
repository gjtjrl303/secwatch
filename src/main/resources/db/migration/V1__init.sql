CREATE TABLE monitored_service (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    name        VARCHAR(100) NOT NULL,
    domain      VARCHAR(255) NOT NULL,
    webhook_url VARCHAR(500) NULL,
    created_at  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT uk_monitored_service_domain UNIQUE (domain)
);

CREATE TABLE dependency (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    service_id BIGINT       NOT NULL,
    ecosystem  VARCHAR(30)  NOT NULL,
    name       VARCHAR(255) NOT NULL,
    version    VARCHAR(100) NOT NULL,
    CONSTRAINT fk_dependency_service FOREIGN KEY (service_id) REFERENCES monitored_service (id) ON DELETE CASCADE,
    CONSTRAINT uk_dependency UNIQUE (service_id, ecosystem, name, version)
);
