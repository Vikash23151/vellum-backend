-- Every microservice owns its own database ( database isolation )

CREATE DATABASE IF NOT EXISTS vellum_auth;
CREATE DATABASE IF NOT EXISTS vellum_post;
CREATE DATABASE IF NOT EXISTS vellum_comment;
CREATE DATABASE IF NOT EXISTS vellum_category;
CREATE DATABASE IF NOT EXISTS vellum_media;
CREATE DATABASE IF NOT EXISTS vellum_newsletter;
CREATE DATABASE IF NOT EXISTS vellum_notification;

GRANT ALL PRIVILEGES ON vellum_auth.*         TO 'vellum'@'%';
GRANT ALL PRIVILEGES ON vellum_post.*         TO 'vellum'@'%';
GRANT ALL PRIVILEGES ON vellum_comment.*      TO 'vellum'@'%';
GRANT ALL PRIVILEGES ON vellum_category.*     TO 'vellum'@'%';
GRANT ALL PRIVILEGES ON vellum_media.*        TO 'vellum'@'%';
GRANT ALL PRIVILEGES ON vellum_newsletter.*   TO 'vellum'@'%';
GRANT ALL PRIVILEGES ON vellum_notification.* TO 'vellum'@'%';

FLUSH PRIVILEGES;