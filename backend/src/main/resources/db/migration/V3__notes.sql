-- Milestone 4: the protected Co-Note notes API.
-- Each note belongs to one Co-Note user (owner_id); users only ever see their own notes.
CREATE TABLE notes (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  owner_id    BIGINT       NOT NULL,
  title       VARCHAR(200) NOT NULL,
  body        TEXT,
  created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (owner_id) REFERENCES users(id) ON DELETE CASCADE,
  INDEX idx_notes_owner (owner_id, updated_at),
  INDEX idx_notes_updated (updated_at)
);
