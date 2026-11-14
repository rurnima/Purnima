CREATE TABLE IF NOT EXISTS auth_keys (
  id INTEGER PRIMARY KEY CHECK (id = 1),
  private_key_pkcs8 TEXT NOT NULL,
  public_jwk TEXT NOT NULL,
  key_id TEXT NOT NULL,
  created_at INTEGER NOT NULL
);
