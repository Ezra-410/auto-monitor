-- Milestone 3 (D-24): users now come from Keycloak (OpenID Connect).
-- external_subject = the Keycloak "sub" claim: a permanent ID that survives renames.
-- password_hash becomes optional: SSO users have no local password.
ALTER TABLE users
  ADD COLUMN external_subject VARCHAR(64) NULL,
  ADD COLUMN email            VARCHAR(255) NULL,
  ADD COLUMN last_login_at    TIMESTAMP NULL,
  MODIFY password_hash        VARCHAR(100) NULL;

CREATE UNIQUE INDEX uk_users_external_subject ON users (external_subject);
