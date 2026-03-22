-- Tabela principal particionada por mês (collected_at)
CREATE TABLE articles
(
    id              UUID          NOT NULL DEFAULT gen_random_uuid(),
    user_id         UUID          NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    source_id       UUID REFERENCES feed_sources (id),
    url             VARCHAR(1024) NOT NULL,
    content_hash    VARCHAR(64)   NOT NULL,
    title           VARCHAR(512),
    body            TEXT,
    summary         TEXT,
    status          VARCHAR(30)   NOT NULL DEFAULT 'PENDING',
    attempts        INT           NOT NULL DEFAULT 0,
    last_attempt_at TIMESTAMP,
    email_sent      BOOLEAN       NOT NULL DEFAULT FALSE,
    collected_at    TIMESTAMP     NOT NULL DEFAULT NOW(),
    summarized_at   TIMESTAMP,

    -- PK deve incluir a coluna de partição (regra do PostgreSQL)
    PRIMARY KEY (id, collected_at),

    -- Evita duplicatas: mesmo usuário, mesmo conteúdo, mesmo mês
    UNIQUE (user_id, content_hash, collected_at)
) PARTITION BY RANGE (collected_at);

-- Partições dos próximos meses
-- Quando o PartitionMaintenanceJob estiver pronto, ele cuida disso automaticamente
-- Por ora criamos manualmente os próximos meses
CREATE TABLE articles_2025_01 PARTITION OF articles
    FOR VALUES FROM
(
    '2025-01-01'
) TO
(
    '2025-02-01'
);

CREATE TABLE articles_2025_02 PARTITION OF articles
    FOR VALUES FROM
(
    '2025-02-01'
) TO
(
    '2025-03-01'
);

CREATE TABLE articles_2025_03 PARTITION OF articles
    FOR VALUES FROM
(
    '2025-03-01'
) TO
(
    '2025-04-01'
);

CREATE TABLE articles_2025_04 PARTITION OF articles
    FOR VALUES FROM
(
    '2025-04-01'
) TO
(
    '2025-05-01'
);

CREATE TABLE articles_2025_05 PARTITION OF articles
    FOR VALUES FROM
(
    '2025-05-01'
) TO
(
    '2025-06-01'
);

CREATE TABLE articles_2025_06 PARTITION OF articles
    FOR VALUES FROM
(
    '2025-06-01'
) TO
(
    '2025-07-01'
);

CREATE TABLE articles_2025_07 PARTITION OF articles
    FOR VALUES FROM
(
    '2025-07-01'
) TO
(
    '2025-08-01'
);

CREATE TABLE articles_2025_08 PARTITION OF articles
    FOR VALUES FROM
(
    '2025-08-01'
) TO
(
    '2025-09-01'
);

CREATE TABLE articles_2025_09 PARTITION OF articles
    FOR VALUES FROM
(
    '2025-09-01'
) TO
(
    '2025-10-01'
);

CREATE TABLE articles_2025_10 PARTITION OF articles
    FOR VALUES FROM
(
    '2025-10-01'
) TO
(
    '2025-11-01'
);

CREATE TABLE articles_2025_11 PARTITION OF articles
    FOR VALUES FROM
(
    '2025-11-01'
) TO
(
    '2025-12-01'
);

CREATE TABLE articles_2025_12 PARTITION OF articles
    FOR VALUES FROM
(
    '2025-12-01'
) TO
(
    '2026-01-01'
);

CREATE TABLE articles_2026_01 PARTITION OF articles
    FOR VALUES FROM
(
    '2026-01-01'
) TO
(
    '2026-02-01'
);

CREATE TABLE articles_2026_02 PARTITION OF articles
    FOR VALUES FROM
(
    '2026-02-01'
) TO
(
    '2026-03-01'
);

CREATE TABLE articles_2026_03 PARTITION OF articles
    FOR VALUES FROM
(
    '2026-03-01'
) TO
(
    '2026-04-01'
);

CREATE TABLE articles_2026_04 PARTITION OF articles
    FOR VALUES FROM
(
    '2026-04-01'
) TO
(
    '2026-05-01'
);

CREATE TABLE articles_2026_05 PARTITION OF articles
    FOR VALUES FROM
(
    '2026-05-01'
) TO
(
    '2026-06-01'
);

CREATE TABLE articles_2026_06 PARTITION OF articles
    FOR VALUES FROM
(
    '2026-06-01'
) TO
(
    '2026-07-01'
);

-- Indexes criados na tabela pai — PostgreSQL replica para cada partição
CREATE INDEX idx_articles_user_id ON articles (user_id);
CREATE INDEX idx_articles_status ON articles (status);
CREATE INDEX idx_articles_collected_at ON articles (collected_at);
CREATE INDEX idx_articles_email_sent ON articles (email_sent);