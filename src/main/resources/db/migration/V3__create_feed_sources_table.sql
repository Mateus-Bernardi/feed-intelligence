CREATE TABLE feed_sources
(
    id         UUID PRIMARY KEY      DEFAULT gen_random_uuid(),
    user_id    UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    type       VARCHAR(20)  NOT NULL, -- 'RSS' ou 'KEYWORD'
    value      VARCHAR(512) NOT NULL, -- URL ou palavra-chave
    active     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP    NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_feed_sources_user_value UNIQUE (user_id, value)
);

CREATE INDEX idx_feed_sources_user_id ON feed_sources (user_id);