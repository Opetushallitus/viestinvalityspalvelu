ALTER TABLE viestit ADD COLUMN IF NOT EXISTS idempotency_key VARCHAR;

CREATE UNIQUE INDEX IF NOT EXISTS idempotency_idx ON viestit (omistaja, idempotency_key);
