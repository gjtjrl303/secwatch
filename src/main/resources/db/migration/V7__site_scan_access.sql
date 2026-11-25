ALTER TABLE project
    ADD COLUMN site_scan_path     VARCHAR(200) NULL,
    ADD COLUMN site_auth_username VARCHAR(100) NULL,
    ADD COLUMN site_auth_password VARCHAR(100) NULL;
