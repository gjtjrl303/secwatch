ALTER TABLE project
    MODIFY COLUMN webhook_url        VARCHAR(1024) NULL,
    MODIFY COLUMN site_auth_password VARCHAR(1024) NULL;
