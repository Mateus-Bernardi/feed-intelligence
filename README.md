# Feed Intelligence

> Agregador de feeds RSS e notícias com sumarização por IA, entrega diária por e-mail, autenticação JWT e cache distribuído.

[![Java](https://img.shields.io/badge/Java-21-orange?logo=openjdk)](https://openjdk.org/projects/jdk/21/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5-brightgreen?logo=springboot)](https://spring.io/projects/spring-boot)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-blue?logo=postgresql)](https://www.postgresql.org/)
[![Redis](https://img.shields.io/badge/Redis-7-red?logo=redis)](https://redis.io/)
[![Docker](https://img.shields.io/badge/Docker-Compose-2496ED?logo=docker)](https://docs.docker.com/compose/)

---

## Sobre o Projeto

O **Feed Intelligence** é um backend completo que coleta artigos de fontes RSS e da NewsAPI, resume cada artigo automaticamente via IA (OpenAI ou Gemini como fallback), e entrega um digest diário personalizado por e-mail para cada usuário.

O projeto foi projetado com foco em **resiliência, escalabilidade horizontal e boas práticas de engenharia**, cobrindo tópicos como locks distribuídos, filas assíncronas, deduplicação em duas camadas, particionamento de tabelas, cache-aside e segurança stateless com JWT.

---

## Funcionalidades

- 📡 **Coleta automática** de feeds RSS e notícias por palavra-chave (NewsAPI) a cada 2 horas
- 🤖 **Sumarização por IA** com OpenAI (fallback automático para Gemini) via fila Redis
- 📧 **Digest diário por e-mail** no horário configurado por cada usuário, com retry automático
- 🔐 **Autenticação JWT** com access token (15 min) + refresh token revogável (7 dias)
- ⚡ **Cache Redis** com padrão cache-aside para leitura de resumos em < 5ms
- 🔒 **Lock distribuído** no cron de coleta para evitar execuções paralelas em múltiplas instâncias
- 🗃️ **Deduplicação em duas camadas**: por URL e por hash de conteúdo (SHA-256)
- 📊 **Particionamento mensal** da tabela de artigos com criação automática de novas partições
- 📋 **Execution Ledger** append-only para rastreabilidade de cada execução dos jobs
- 📄 **API documentada** com Swagger UI (SpringDoc OpenAPI)

---

## Stack Tecnológica

| Camada           | Tecnologia                          | Motivo                                                      |
|------------------|-------------------------------------|-------------------------------------------------------------|
| Backend          | Spring Boot 3.5 (Java 21)           | LTS, virtual threads, records nativos                       |
| Banco de dados   | PostgreSQL 16                       | Particionamento nativo, advisory locks, JSONB               |
| Cache / Filas    | Redis 7                             | Cache de resumos, locks distribuídos, fila LPUSH/BRPOP      |
| Autenticação     | Spring Security + JJWT 0.12         | Controle de acesso stateless com refresh token revogável    |
| HTTP Client      | WebClient (Reactor)                 | Async, timeout e retry nativos                              |
| Parser RSS       | Rome Library 2.1                    | Padrão Java para RSS/Atom                                   |
| Migrations       | Flyway                              | Versionamento de schema com rollback controlado             |
| E-mail           | Spring Mail + Resend (ou AWS SES)   | Envio transacional com retry queue                          |
| IA               | OpenAI API → Gemini (fallback)      | Anti-fragilidade via fallback entre providers               |
| Containerização  | Docker + Docker Compose             | Ambiente de desenvolvimento reproduzível                    |
| Documentação API | SpringDoc OpenAPI (Swagger UI)      | Exploração interativa da API                                |

---

## Arquitetura

### Visão geral dos fluxos

```
┌─────────────────────────────────────────────────────────────────────────┐
│                            CLIENTES (HTTP)                              │
└───────────────────────────────┬─────────────────────────────────────────┘
                                │ REST API
                                ▼
┌─────────────────────────────────────────────────────────────────────────┐
│  Spring Boot App                                                        │
│                                                                         │
│  ┌───────────┐  ┌─────────────┐  ┌───────────┐  ┌──────────────────┐  │
│  │AuthController│ │FeedController│ │ArticleCtrl│  │AdminController   │  │
│  └─────┬─────┘  └──────┬──────┘  └─────┬─────┘  └────────┬─────────┘  │
│        │               │               │                  │            │
│        ▼               ▼               ▼                  ▼            │
│  ┌──────────────────────────────────────────────────────────────────┐  │
│  │                     Services / Domain Layer                      │  │
│  └──────────────────────────────────────────────────────────────────┘  │
│                                                                         │
│  ┌──────────────────────────────────────────────────────────────────┐  │
│  │                      Scheduled Jobs                              │  │
│  │  CollectorJob (2h) ──► RssFetcher / NewsApiFetcher               │  │
│  │  DigestJob (cada hora) ──► EmailDispatcher                       │  │
│  │  PartitionMaintenanceJob (diário 01h)                            │  │
│  └──────────────────────────────────────────────────────────────────┘  │
│                                                                         │
│  ┌──────────────────────────────────────────────────────────────────┐  │
│  │                      SummarizerWorker                            │  │
│  │       BRPOP queue:summarizer ──► OpenAI / Gemini fallback        │  │
│  └──────────────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────────────┘
         │                        │
         ▼                        ▼
  ┌────────────┐          ┌──────────────┐
  │ PostgreSQL │          │    Redis     │
  │ (primary)  │          │  cache/fila  │
  │ (replica)  │          └──────────────┘
  └────────────┘
```

### Estrutura de pacotes

```
src/main/java/com/feedintelligence/
├── config/
│   ├── SecurityConfig.java          # Spring Security + filtros JWT
│   ├── RedisConfig.java             # Configuração do RedisTemplate
│   ├── DataSourceConfig.java        # Primary + Replica DataSource
│   └── SchedulerConfig.java         # Habilita @Scheduled
│
├── auth/
│   ├── AuthController.java          # POST /auth/register, /auth/login...
│   ├── AuthService.java
│   ├── JwtService.java              # Geração e validação de tokens
│   ├── JwtAuthFilter.java           # Filtro de autenticação
│   └── dto/
│       ├── RegisterRequest.java
│       ├── LoginRequest.java
│       └── AuthResponse.java        # { accessToken, refreshToken }
│
├── user/
│   ├── User.java                    # Entidade + roles
│   ├── UserRepository.java
│   └── UserService.java
│
├── feed/
│   ├── FeedSource.java              # Entidade: URL RSS ou keyword NewsAPI
│   ├── FeedSourceRepository.java
│   ├── FeedSourceController.java    # CRUD de fontes por usuário
│   ├── FeedSourceService.java
│   └── dto/
│       ├── FeedSourceRequest.java
│       └── FeedSourceResponse.java
│
├── article/
│   ├── Article.java                 # Entidade com status e content_hash
│   ├── ArticleRepository.java
│   ├── ArticleService.java
│   └── ArticleStatus.java           # Enum: PENDING, SUMMARIZED, FAILED
│
├── collector/
│   ├── CollectorJob.java            # @Scheduled: coleta a cada 2h
│   ├── RssFetcherService.java       # Busca e parseia feeds RSS
│   ├── NewsApiFetcherService.java   # Busca via NewsAPI
│   └── FetcherAdapter.java          # Interface: normaliza RSS e JSON
│
├── summarizer/
│   ├── SummarizerWorker.java        # Worker: consome fila Redis (BRPOP)
│   ├── OpenAiSummarizerService.java
│   ├── GeminiSummarizerService.java # Fallback
│   └── AiSummarizerService.java     # Interface comum
│
├── digest/
│   ├── DigestJob.java               # @Scheduled: disparo matinal
│   ├── DigestService.java           # Monta e-mail por usuário
│   └── EmailDispatcherService.java  # Envio + retry queue
│
├── observability/
│   ├── ExecutionLedger.java         # Entidade append-only
│   ├── ExecutionLedgerRepository.java
│   └── ExecutionLedgerService.java  # Registra cada run de cron
│
├── maintenance/
│   └── PartitionMaintenanceJob.java # @Scheduled: cria partição do próximo mês
│
└── common/
    ├── exception/
    │   ├── GlobalExceptionHandler.java
    │   └── AppException.java
    └── lock/
        └── DistributedLockService.java  # Redis SETNX wrapper
```

---

## Modelo de Dados

### `users`

```sql
CREATE TABLE users (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email       VARCHAR(255) UNIQUE NOT NULL,
    password    VARCHAR(255) NOT NULL,         -- bcrypt
    role        VARCHAR(50) DEFAULT 'USER',
    digest_hour INT DEFAULT 7,                 -- hora do envio (0-23)
    active      BOOLEAN DEFAULT TRUE,
    created_at  TIMESTAMP DEFAULT NOW()
);
```

### `refresh_tokens`

```sql
CREATE TABLE refresh_tokens (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID REFERENCES users(id) ON DELETE CASCADE,
    token       VARCHAR(512) UNIQUE NOT NULL,
    expires_at  TIMESTAMP NOT NULL,
    revoked     BOOLEAN DEFAULT FALSE,
    created_at  TIMESTAMP DEFAULT NOW()
);
```

### `feed_sources`

```sql
CREATE TABLE feed_sources (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID REFERENCES users(id) ON DELETE CASCADE,
    type        VARCHAR(20) NOT NULL,    -- 'RSS' ou 'KEYWORD'
    value       VARCHAR(512) NOT NULL,   -- URL ou palavra-chave
    active      BOOLEAN DEFAULT TRUE,
    created_at  TIMESTAMP DEFAULT NOW()
);
```

### `articles` — particionada por mês (RANGE)

```sql
CREATE TABLE articles (
    id              UUID NOT NULL DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    source_id       UUID REFERENCES feed_sources(id),
    url             VARCHAR(1024) NOT NULL,
    content_hash    VARCHAR(64) NOT NULL,      -- SHA-256(título + corpo[:200])
    title           VARCHAR(512),
    body            TEXT,
    summary         TEXT,                      -- preenchido pelo SummarizerWorker
    status          VARCHAR(30) DEFAULT 'PENDING',
    attempts        INT DEFAULT 0,
    last_attempt_at TIMESTAMP,
    email_sent      BOOLEAN DEFAULT FALSE,
    collected_at    TIMESTAMP DEFAULT NOW(),
    summarized_at   TIMESTAMP,
    PRIMARY KEY (id, collected_at),
    UNIQUE (user_id, content_hash, collected_at)
) PARTITION BY RANGE (collected_at);
```

### `email_retry_queue`

```sql
CREATE TABLE email_retry_queue (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID REFERENCES users(id),
    payload     TEXT NOT NULL,              -- HTML do e-mail serializado
    attempts    INT DEFAULT 0,
    next_try_at TIMESTAMP DEFAULT NOW(),
    created_at  TIMESTAMP DEFAULT NOW()
);
```

### `execution_ledger`

```sql
CREATE TABLE execution_ledger (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    job_name         VARCHAR(100) NOT NULL,  -- 'COLLECTOR', 'SUMMARIZER', 'DIGEST'
    started_at       TIMESTAMP NOT NULL,
    finished_at      TIMESTAMP,
    status           VARCHAR(20),            -- 'SUCCESS', 'PARTIAL', 'FAILED'
    articles_found   INT DEFAULT 0,
    articles_skipped INT DEFAULT 0,
    errors           TEXT,                   -- JSON com erros capturados
    created_at       TIMESTAMP DEFAULT NOW()
);
```

---

## Fluxos dos Jobs

### Job 1 — Coleta (`CollectorJob`) — a cada 2h

```
1. Adquire lock distribuído Redis (SETNX "lock:collector", TTL 2h)
   └─ Se já travado: aborta silenciosamente (outra instância rodando)

2. Abre registro no ExecutionLedger (status = RUNNING)

3. Para cada FeedSource ativo:
   ├── RSS    → RssFetcherService  (WebClient + timeout 5s + retry 2x)
   └── KEYWORD → NewsApiFetcherService (mesma estratégia)
       └── Ambos normalizam via FetcherAdapter → ArticleDTO

4. Para cada ArticleDTO:
   ├── Calcula content_hash = SHA-256(title + body[:200])
   ├── Checa no banco: (user_id, content_hash) já existe? → skipped++
   └── Se novo: salva Article(PENDING) → LPUSH queue:summarizer <articleId>

5. Atualiza ExecutionLedger (found / skipped / errors)
6. Libera lock: DEL lock:collector
```

### Worker — Sumarização (`SummarizerWorker`) — thread dedicada

```
1. BRPOP queue:summarizer 30  (bloqueia até 30s)

2. Busca Article pelo id recebido
   └─ status != PENDING? descarta

3. Chama OpenAiSummarizerService.summarize(prompt)
   ├── Erro 429 (rate limit): backoff exponencial 1s → 2s → 4s
   ├── Erro 5xx: fallback imediato para GeminiSummarizerService
   └── Ambos falham: attempts++; após 3x → status = FAILED_PERMANENT

4. Sucesso:
   ├── Salva summary, status = SUMMARIZED
   └── SET resumos:{userId}:{hoje} <json> EX 86400  (atualiza cache)
```

### Job 2 — Digest Matinal (`DigestJob`) — a cada hora

```
1. Busca usuários com digest_hour = CURRENT_HOUR e active = true

2. Para cada usuário:
   ├── Busca articles: status = SUMMARIZED AND email_sent = false
   ├── Monta HTML com template Thymeleaf
   ├── Envia via EmailDispatcherService
   │   ├── Sucesso: marca articles como email_sent = true
   │   └── Falha: insere em email_retry_queue (next_try_at = now + 15min)

3. Processa email_retry_queue (attempts < 3, next_try_at <= NOW())
   ├── Sucesso: remove da fila
   └── Falha: attempts++, next_try_at = now + 15min × 2^attempts
```

### Job 3 — Manutenção de Partições (`PartitionMaintenanceJob`) — diário às 01h

```
1. Calcula primeiro dia do próximo mês
2. Executa: CREATE TABLE IF NOT EXISTS articles_YYYY_MM
            PARTITION OF articles FOR VALUES FROM (...) TO (...)
   └── IF NOT EXISTS garante idempotência
```

---

## Autenticação e Autorização

### Fluxo JWT

```
POST /auth/register  →  cria usuário, retorna { accessToken, refreshToken }
POST /auth/login     →  valida senha, retorna tokens
POST /auth/refresh   →  valida refreshToken no banco, emite novo accessToken
POST /auth/logout    →  marca refreshToken como revoked = true
```

### Filtro de autenticação

```
Request → JwtAuthFilter
    ├── Extrai "Authorization: Bearer <token>"
    ├── JwtService.validateToken(token)
    │       ├── Verifica assinatura (chave via JWT_SECRET)
    │       ├── Verifica expiração
    │       └── Extrai userId e role
    ├── Seta SecurityContextHolder
    └── Passa para controller
```

### Matriz de autorização

| Endpoint                     | Acesso           |
|------------------------------|------------------|
| `POST /auth/**`              | Público          |
| `GET/POST/DELETE /api/feeds` | `ROLE_USER`      |
| `GET /api/articles`          | `ROLE_USER`      |
| `PUT /api/users/me`          | `ROLE_USER`      |
| `GET /api/admin/**`          | `ROLE_ADMIN`     |

> - **accessToken**: expira em **15 minutos**
> - **refreshToken**: expira em **7 dias**, armazenado no banco — pode ser revogado imediatamente no logout

---

## Cache — Padrão Cache-Aside

```
Leitura (GET /api/articles):
  1. GET resumos:{userId}:{data}
     ├── Cache HIT  → retorna em < 5ms
     └── Cache MISS → query no banco (replicaDataSource) → SET no Redis (TTL 24h) → retorna

Escrita (fim da sumarização):
  - SummarizerWorker escreve no Redis logo após salvar no banco
  - Garante que a próxima leitura sempre encontra no cache
```

---

## Redundância (Primary / Replica)

A aplicação configura dois `DataSource` explícitos — `primaryDataSource` (escritas) e `replicaDataSource` (leituras). Em desenvolvimento, ambos apontam para a mesma instância PostgreSQL. Em produção, a simples troca de variável de ambiente redireciona para um RDS Read Replica.

```java
// DigestService e ArticleService de leitura:
@Autowired
@Qualifier("replicaDataSource")
private DataSource replicaDs;
```

### O que mudaria em produção

| Componente       | Dev (Docker Compose)        | Produção                          |
|------------------|-----------------------------|-----------------------------------|
| PostgreSQL       | Única instância             | RDS Multi-AZ + Read Replica       |
| Redis            | Instância única             | ElastiCache / Redis Sentinel       |
| App              | 1 container                 | N instâncias atrás de load balancer |
| Locks            | Redis SETNX (já preparado)  | Mesmo código, funciona em N pods   |

---

## Tratamento de Erros

| Camada          | Cenário                      | Ação                                                  |
|-----------------|------------------------------|-------------------------------------------------------|
| Coleta RSS      | Timeout > 5s                 | Loga, incrementa erro no ledger, continua             |
| Coleta          | HTTP 4xx/5xx                 | Retry 2x com backoff 2s                               |
| Sumarização     | OpenAI 429                   | Exponential backoff: 1s → 2s → 4s                     |
| Sumarização     | OpenAI 5xx                   | Fallback imediato para Gemini                         |
| Sumarização     | Ambos falham (3×)            | `FAILED_PERMANENT` — não reprocessa                   |
| E-mail          | Falha no envio               | Insere em `email_retry_queue`                         |
| E-mail          | 3 tentativas falhas           | Loga como falha permanente, remove da fila            |
| API REST        | Erros não tratados           | `GlobalExceptionHandler` → JSON padronizado           |
| Particionamento | Partição ausente             | `PartitionMaintenanceJob` cria com `IF NOT EXISTS`    |

---

## API Reference

### Autenticação

| Método | Endpoint           | Descrição                       | Auth     |
|--------|--------------------|---------------------------------|----------|
| POST   | `/auth/register`   | Cria conta                      | Público  |
| POST   | `/auth/login`      | Login, retorna tokens           | Público  |
| POST   | `/auth/refresh`    | Renova accessToken              | Público  |
| POST   | `/auth/logout`     | Revoga refreshToken             | Bearer   |

### Fontes de Notícias

| Método | Endpoint             | Descrição                            | Auth        |
|--------|----------------------|--------------------------------------|-------------|
| GET    | `/api/feeds`         | Lista fontes do usuário logado       | Bearer      |
| POST   | `/api/feeds`         | Adiciona nova fonte (RSS ou keyword) | Bearer      |
| DELETE | `/api/feeds/{id}`    | Remove fonte (soft delete)           | Bearer      |

### Artigos e Resumos

| Método | Endpoint               | Descrição                               | Auth    |
|--------|------------------------|-----------------------------------------|---------|
| GET    | `/api/articles`        | Lista resumos (paginado, cache-aside)   | Bearer  |
| GET    | `/api/articles/{id}`   | Detalhe de um artigo                    | Bearer  |

### Usuário

| Método | Endpoint          | Descrição                          | Auth   |
|--------|-------------------|------------------------------------|--------|
| GET    | `/api/users/me`   | Perfil do usuário autenticado      | Bearer |
| PUT    | `/api/users/me`   | Atualiza horário do digest (0–23)  | Bearer |

### Admin

| Método | Endpoint                | Descrição                          | Auth         |
|--------|-------------------------|------------------------------------|--------------|
| GET    | `/api/admin/ledger`     | Histórico de execuções dos jobs    | ROLE_ADMIN   |
| GET    | `/api/admin/metrics`    | Contadores globais                 | ROLE_ADMIN   |

> A documentação interativa completa está disponível em **`/swagger-ui.html`** após subir a aplicação.

---

## Como Executar (Desenvolvimento)

### Pré-requisitos

- Java 21+
- Maven 3.9+
- Docker + Docker Compose

### 1. Subir as dependências

```bash
docker compose up -d
```

Isso sobe PostgreSQL (porta 5432), PgAdmin (porta 5050) e Redis (porta 6379).

### 2. Configurar variáveis de ambiente

Crie um arquivo `.env` na raiz (ou use o exemplo):

```env
# Banco
DATABASE_URL=jdbc:postgresql://localhost:5432/feedintelligence
DATABASE_USERNAME=postgres
DATABASE_PASSWORD=postgres

# Primary / Replica (em dev apontam para o mesmo host)
SPRING_DATASOURCE_PRIMARY_URL=jdbc:postgresql://localhost:5432/feedintelligence
SPRING_DATASOURCE_REPLICA_URL=jdbc:postgresql://localhost:5432/feedintelligence

# Redis
REDIS_HOST=localhost
REDIS_PORT=6379

# JWT
JWT_SECRET=sua-chave-secreta-com-pelo-menos-32-caracteres
JWT_EXPIRATION_MS=900000         # 15 minutos
JWT_REFRESH_EXPIRATION_MS=604800000  # 7 dias

# APIs externas
NEWS_API_KEY=sua-chave-newsapi
OPENAI_API_KEY=sua-chave-openai
GEMINI_API_KEY=sua-chave-gemini

# E-mail
RESEND_API_KEY=sua-chave-resend
EMAIL_FROM=digest@feedintelligence.com
```

### 3. Rodar a aplicação

```bash
./mvnw spring-boot:run
```

O Flyway executa as migrations automaticamente na primeira inicialização.

### 4. Acessar

| Serviço         | URL                             |
|-----------------|---------------------------------|
| API REST        | http://localhost:8080           |
| Swagger UI      | http://localhost:8080/swagger-ui.html |
| PgAdmin         | http://localhost:5050           |

---

## Variáveis de Ambiente — Referência Completa

| Variável                         | Descrição                                          | Obrigatória |
|----------------------------------|----------------------------------------------------|-------------|
| `DATABASE_URL`                   | JDBC URL do banco principal                        | ✅           |
| `DATABASE_USERNAME`              | Usuário do banco                                   | ✅           |
| `DATABASE_PASSWORD`              | Senha do banco                                     | ✅           |
| `SPRING_DATASOURCE_PRIMARY_URL`  | JDBC URL para escritas                             | ✅           |
| `SPRING_DATASOURCE_REPLICA_URL`  | JDBC URL para leituras                             | ✅           |
| `REDIS_HOST`                     | Host do Redis                                      | ✅           |
| `REDIS_PORT`                     | Porta do Redis (padrão: 6379)                      | ✅           |
| `JWT_SECRET`                     | Chave HMAC-SHA256 (mínimo 32 chars)                | ✅           |
| `JWT_EXPIRATION_MS`              | Expiração do accessToken em ms (padrão: 900000)    | ✅           |
| `JWT_REFRESH_EXPIRATION_MS`      | Expiração do refreshToken em ms (padrão: 604800000)| ✅           |
| `NEWS_API_KEY`                   | Chave da NewsAPI                                   | ✅           |
| `OPENAI_API_KEY`                 | Chave da OpenAI                                    | ✅           |
| `GEMINI_API_KEY`                 | Chave do Gemini (fallback de sumarização)          | ✅           |
| `RESEND_API_KEY`                 | Chave do Resend para envio de e-mail               | ✅           |
| `EMAIL_FROM`                     | Endereço de origem dos e-mails                     | ✅           |

---

## Decisões Técnicas

### Por que lock distribuído no `CollectorJob`?

Sem o lock, múltiplas instâncias da aplicação (ex: em produção com load balancer) executariam o job ao mesmo tempo, coletando os mesmos artigos em paralelo. O Redis `SETNX` garante que apenas uma instância executa por vez.

```java
if (!lockService.acquireLock("lock:collector", Duration.ofHours(2))) {
    log.warn("Collector já está rodando, abortando execução duplicada.");
    return;
}
try {
    // executa coleta
} finally {
    lockService.releaseLock("lock:collector");
}
```

---

### Por que dedup em duas camadas?

A URL pode ser diferente para o mesmo conteúdo (parâmetros UTM, redirecionamentos, espelhos). O `content_hash` (SHA-256 do título + primeiros 200 chars do corpo) garante dedup mesmo quando a URL muda.

```
Camada 1: URL → checa se a URL já existe para aquele usuário
Camada 2: content_hash → checa se o conteúdo já existe independente da URL
```

---

### Por que `BRPOP` ao invés de polling?

O `BRPOP` bloqueia a thread até um item aparecer na fila (timeout de 30s). Com polling, o worker faria queries constantes ao Redis mesmo com fila vazia, consumindo CPU e conexões desnecessariamente.

---

### Por que particionar a tabela `articles`?

Artigos são lidos principalmente por data recente. O particionamento mensal mantém índices menores por partição e permite `DROP TABLE` na partição antiga sem necessidade de `VACUUM` — operação muito mais rápida que `DELETE` em tabela grande.

---

### Por que fallback OpenRouter → Gemini?

Anti-fragilidade: o sistema continua funcionando se um provider cair ou atingir rate limit. Os dois providers são independentes — uma falha no OpenAI não afeta o Gemini.

---

### Por que refresh token no banco?

Para permitir logout real. JWT puro é stateless e não pode ser invalidado antes de expirar. Armazenando o refresh token no banco, podemos revogar o acesso imediatamente no logout.

---

### Por que soft delete em `FeedSource`?

Deletar o registro de verdade quebraria a referência `source_id` nos artigos coletados. Com `active = false` mantemos o histórico intacto.

---

## Dependências Principais

```xml
<!-- Web + Security -->
spring-boot-starter-web
spring-boot-starter-security
spring-boot-starter-validation

<!-- Dados -->
spring-boot-starter-data-jpa
spring-boot-starter-data-redis
flyway-core + flyway-database-postgresql
postgresql (driver)

<!-- JWT -->
jjwt-api / jjwt-impl / jjwt-jackson (0.12.6)

<!-- RSS -->
rome (2.1.0)

<!-- Documentação -->
springdoc-openapi-starter-webmvc-ui (2.8.6)

<!-- Utilitários -->
lombok
spring-dotenv (carrega .env automaticamente)
spring-boot-starter-mail
spring-boot-devtools (desenvolvimento)
```

---

## Contribuindo

1. Fork o repositório
2. Crie uma branch: `git checkout -b feat/minha-feature`
3. Commit: `git commit -m "feat(scope): description"`
4. Push: `git push origin feat/minha-feature`
5. Abra um Pull Request

---

## Licença

Distribuído sob a licença MIT. Consulte o arquivo `LICENSE` para mais detalhes.
