ALTER TABLE project
    DROP COLUMN site_auth_username,
    DROP COLUMN site_auth_password,
    MODIFY COLUMN webhook_url VARCHAR(1024) NULL;
