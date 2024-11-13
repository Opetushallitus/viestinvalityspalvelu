DROP INDEX IF EXISTS idempotenct_idx;
CREATE UNIQUE INDEX IF NOT EXISTS idempotency_idx ON viestit (omistaja, idempotency_key) WHERE idempotency_key IS NOT NULL;
