RENAME TABLE monitored_service TO project;
ALTER TABLE project RENAME INDEX uk_monitored_service_domain TO uk_project_domain;

ALTER TABLE dependency DROP FOREIGN KEY fk_dependency_service;
ALTER TABLE dependency RENAME COLUMN service_id TO project_id;
ALTER TABLE dependency
    ADD CONSTRAINT fk_dependency_project FOREIGN KEY (project_id) REFERENCES project (id) ON DELETE CASCADE;
