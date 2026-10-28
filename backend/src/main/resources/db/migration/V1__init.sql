CREATE TABLE roles (
  id   BIGINT AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(50) NOT NULL UNIQUE
);

CREATE TABLE users (
  id            BIGINT AUTO_INCREMENT PRIMARY KEY,
  username      VARCHAR(100) NOT NULL UNIQUE,
  password_hash VARCHAR(100) NOT NULL,
  display_name  VARCHAR(200),
  created_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE user_roles (
  user_id BIGINT NOT NULL,
  role_id BIGINT NOT NULL,
  PRIMARY KEY (user_id, role_id),
  FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
  FOREIGN KEY (role_id) REFERENCES roles(id) ON DELETE CASCADE
);

CREATE TABLE projects (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  name        VARCHAR(200) NOT NULL,
  description TEXT,
  owner_id    BIGINT NOT NULL,
  created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (owner_id) REFERENCES users(id)
);

CREATE TABLE repositories (
  id              BIGINT AUTO_INCREMENT PRIMARY KEY,
  project_id      BIGINT NOT NULL,
  url             VARCHAR(500) NOT NULL,
  branch          VARCHAR(200) NOT NULL DEFAULT 'main',
  token_encrypted VARBINARY(2000),
  created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE CASCADE
);

CREATE TABLE objectives (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  project_id  BIGINT NOT NULL,
  title       VARCHAR(300) NOT NULL,
  description TEXT,
  status      VARCHAR(20) NOT NULL DEFAULT 'PLANNED',
  priority    INT NOT NULL DEFAULT 3,
  due_date    DATE,
  created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE CASCADE,
  INDEX idx_obj_project (project_id, status)
);

CREATE TABLE milestones (
  id           BIGINT AUTO_INCREMENT PRIMARY KEY,
  objective_id BIGINT NOT NULL,
  title        VARCHAR(300) NOT NULL,
  due_date     DATE,
  status       VARCHAR(20) NOT NULL DEFAULT 'PLANNED',
  FOREIGN KEY (objective_id) REFERENCES objectives(id) ON DELETE CASCADE
);

CREATE TABLE tasks (
  id           BIGINT AUTO_INCREMENT PRIMARY KEY,
  project_id   BIGINT NOT NULL,
  objective_id BIGINT NULL,
  title        VARCHAR(300) NOT NULL,
  description  TEXT,
  status       VARCHAR(20) NOT NULL DEFAULT 'TODO',
  assignee_id  BIGINT NULL,
  due_date     DATE,
  created_by   VARCHAR(10) NOT NULL DEFAULT 'USER',
  created_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE CASCADE,
  FOREIGN KEY (objective_id) REFERENCES objectives(id) ON DELETE SET NULL,
  FOREIGN KEY (assignee_id) REFERENCES users(id) ON DELETE SET NULL,
  INDEX idx_task_project (project_id, status)
);

CREATE TABLE bugs (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  project_id  BIGINT NOT NULL,
  title       VARCHAR(300) NOT NULL,
  description TEXT,
  severity    VARCHAR(10) NOT NULL DEFAULT 'MEDIUM',
  status      VARCHAR(20) NOT NULL DEFAULT 'OPEN',
  task_id     BIGINT NULL,
  created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE CASCADE,
  FOREIGN KEY (task_id) REFERENCES tasks(id) ON DELETE SET NULL,
  INDEX idx_bug_project (project_id, status)
);

CREATE TABLE conversations (
  id         BIGINT AUTO_INCREMENT PRIMARY KEY,
  project_id BIGINT NOT NULL,
  user_id    BIGINT NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uq_conv (project_id, user_id),
  FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE CASCADE,
  FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE TABLE messages (
  id              BIGINT AUTO_INCREMENT PRIMARY KEY,
  conversation_id BIGINT NOT NULL,
  author          VARCHAR(10) NOT NULL,
  content         TEXT NOT NULL,
  created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (conversation_id) REFERENCES conversations(id) ON DELETE CASCADE,
  INDEX idx_msg_conv (conversation_id, id)
);
