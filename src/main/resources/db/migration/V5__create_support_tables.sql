-- Fila de retry para e-mails que falharam
CREATE TABLE email_retry_queue
(
    id          UUID PRIMARY KEY   DEFAULT gen_random_uuid(),
    user_id     UUID      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    payload     TEXT      NOT NULL,
    attempts    INT       NOT NULL DEFAULT 0,
    next_try_at TIMESTAMP NOT NULL DEFAULT NOW(),
    created_at  TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_email_retry_next_try ON email_retry_queue (next_try_at);

-- Ledger de execuções dos jobs (observabilidade)
CREATE TABLE execution_ledger
(
    id               UUID PRIMARY KEY      DEFAULT gen_random_uuid(),
    job_name         VARCHAR(100) NOT NULL,
    started_at       TIMESTAMP    NOT NULL,
    finished_at      TIMESTAMP,
    status           VARCHAR(20),
    articles_found   INT          NOT NULL DEFAULT 0,
    articles_skipped INT          NOT NULL DEFAULT 0,
    errors           TEXT,
    created_at       TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_execution_ledger_job_name ON execution_ledger (job_name);
CREATE INDEX idx_execution_ledger_started_at ON execution_ledger (started_at);