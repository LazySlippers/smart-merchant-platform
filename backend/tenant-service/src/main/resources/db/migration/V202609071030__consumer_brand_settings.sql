CREATE TABLE consumer_brand_settings (
 tenant_id BIGINT PRIMARY KEY,
 display_name VARCHAR(128) NOT NULL,
 logo_url VARCHAR(1000) NOT NULL DEFAULT '',
 theme_color VARCHAR(7) NOT NULL DEFAULT '#284f3d',
 headline VARCHAR(80) NOT NULL DEFAULT '把喜欢的好物带进日常。',
 contact_phone VARCHAR(32) NOT NULL DEFAULT '',
 version INT NOT NULL DEFAULT 0
);
