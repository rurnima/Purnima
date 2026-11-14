PRAGMA foreign_keys = ON;

CREATE TABLE IF NOT EXISTS memory_items (
  id TEXT PRIMARY KEY NOT NULL,
  user_id TEXT NOT NULL,
  content TEXT NOT NULL,
  category TEXT NOT NULL DEFAULT 'general',
  source TEXT NOT NULL DEFAULT 'conversation',
  consented_at TEXT NOT NULL,
  created_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ', 'now')),
  updated_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ', 'now')),
  archived_at TEXT,
  CHECK (length(content) BETWEEN 1 AND 12000),
  CHECK (length(category) BETWEEN 1 AND 80),
  CHECK (length(source) BETWEEN 1 AND 80)
);

CREATE INDEX IF NOT EXISTS idx_memory_items_user_active
  ON memory_items(user_id, archived_at, updated_at DESC);

CREATE TABLE IF NOT EXISTS memory_events (
  id TEXT PRIMARY KEY NOT NULL,
  user_id TEXT NOT NULL,
  memory_id TEXT,
  event_type TEXT NOT NULL,
  occurred_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ', 'now')),
  CHECK (event_type IN ('created', 'updated', 'archived', 'deleted'))
);

CREATE INDEX IF NOT EXISTS idx_memory_events_user_time
  ON memory_events(user_id, occurred_at DESC);
