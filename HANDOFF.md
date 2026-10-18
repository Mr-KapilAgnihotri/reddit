# HANDOFF — Reddit clone: make the full Kafka → ML → Kafka pipeline run end-to-end

> **For Claude Code (running locally on Windows, `D:\Reddit_clone`).**
> This file was written by a Claude cloud session that could edit the code but could
> **not** compile Java or run Docker: Maven Central, Docker Hub and Hugging Face were blocked there.
> Everything below marked ✅ was verified. Everything marked ⚠️ is written but **not yet
> compiled or run**. Your job is to verify it, fix anything that breaks, and finish the checklist.

## Status — verified locally by Claude Code (2026-10-04) ✅

All five goals below are met. Results:
- `mvn clean test`: **71 tests, 0 failures** (unit tests plus `*IT` on Testcontainers). Run twice, green both times.
- `pytest`: **56 passed**.
- `docker compose up -d --build`: all 7 services are **healthy**.
- Smoke test: `"what the fuck is this"` became `"what the **** is this"` and `"damn hello"` became `"**** hello"`, with `moderated=True` within 5 s. The row has `is_moderated=t` and a 384-dim embedding.
- UI at :8080 was clicked through: sign-up, post (the "checking…" badge turns into "moderated"), vote, comment, communities.

The cloud pass was verified as written. The fixes below were needed on top of it:
1. **ZooKeeper was never healthy.** The healthcheck's `ruok` command isn't whitelisted, so Kafka and everything after it never started.
   Fixed with `KAFKA_OPTS=-Dzookeeper.4lw.commands.whitelist=ruok`.
2. **`application.properties` was read-only on disk.** Every Maven build after the first failed in `clean`/`resources`. The attribute is cleared.
3. **Login didn't check `isVerified`.** The existing IT expects a 400 "Email not verified", so `AuthService.login` now rejects unverified users.
4. **Admin was never created on a fresh DB.** `AdminDataInitializer` ran before `RoleDataInitializer` and found no ADMIN role.
   Fixed with `@DependsOn("roleDataInitializer")`.
5. **Lost update reverted moderation.** `getPost` did `setViewCount(+1)` + `save(post)`, which rewrote every column. A GET racing the
   moderation consumer wrote the stale text and `is_moderated=false` back.
   Fixed with an atomic `incrementViewCount` query and `@DynamicUpdate` on `Post`.
6. **Stale Redis caches.** A feed or detail read racing the moderation commit re-cached the unmasked post for 5–10 min. The UI hit
   this on almost every post, because it reloads the feed right after posting. Fixed in two ways:
   - The cache manager is now `transactionAware()`.
   - Responses that contain a post younger than `PostService.MODERATION_WINDOW` (60 s) are no longer cached.
7. **`POST /api/auth/verify?token=` without a JSON body returned 415.** This is the smoke test's own call. A non-JSON handler was added.
8. **Test infrastructure problems:**
   - Per-class `@Container` stopped Postgres/Redis under Spring's cached context, so the second IT class got "connection refused".
     Switched to singleton containers.
   - `KafkaPipelineIT` double-encoded its JSON, so the consumer was never really exercised.
   - `RedditApplicationTests` never ran, because surefire didn't include `*Tests`.

New tests:
- `PostModerationPipelineIT` covers the full pipeline with ML replies, embeddings and pgvector recommendations, the race regression,
  roles/admin, health and static UI.
- `ModerationResultEventJsonTest` is the `isModerated` JSON contract regression.
- `EmbeddingConsumerTest`.
- `AuthServiceLoginTest`.
- Additional `PostServiceModerationTest` cases.

## Goal (definition of done)

1. `docker compose up -d --build` starts **postgres, redis, zookeeper, kafka, mailpit, ml-service, backend**, and all report healthy.
2. Register → log in → `POST /api/posts` with profanity → within ~5 s `GET /api/posts/{id}`
   returns masked text, `moderated: true`, and `posts.embedding` is filled in PostgreSQL.
3. `cd backend && mvn test` passes (unit + `*IT` tests; the ITs need Docker Desktop running).
4. `cd ml-service && python -m pytest -q` passes.
5. The frontend at **http://localhost:8080/** works: sign up, post, see the "moderated" badge, vote, comment, communities.

## Architecture

```
Browser (static UI on :8080)
   │
Spring Boot API ──► PostgreSQL (pgvector) / Redis cache
   │  publishes post-created  {postId, title, text, type}
   ▼
Kafka ──► Python ML service (FastAPI, :8000)
             • better_profanity (offline, always on)
             • Groq LLM (optional, when GROQ_API_KEY is set)
             • 384-dim hashing embedding (no model download)
          ◄── publishes post-moderated {postId, maskedText, maskedTitle, isModerated, categories, severity, reason, provider}
          ◄── publishes post-embedding {postId, embedding[384]}
   ▼
Spring Boot consumers ──► UPDATE posts (display_text, title, is_moderated, embedding) + evict caches
```

## What was changed in this pass

### ML service (`ml-service/`) ✅ verified: 56 pytest tests pass (Python 3.11)
- **Offline model replaced by the Groq LLM, at the user's request.** `app/llm_client.py` calls any
  OpenAI-compatible API (default Groq, `llama-3.1-8b-instant`). It uses JSON mode and falls back
  to the profanity filter on any error. To switch to xAI Grok, set `LLM_BASE_URL=https://api.x.ai/v1`
  and `LLM_MODEL=grok-3-mini`.
- **torch / sentence-transformers removed.** `app/embedder.py` is now a deterministic MD5 hashing-trick
  embedding (384 dims, L2-normalised). The Docker image goes from about 2 GB to about 200 MB, and
  nothing is downloaded at runtime.
- **Title is moderated too.** `maskedTitle` is returned when a title is sent.
- **Bug fixed: consumer loop.** It used `while True` around an iterator, which hung the unit tests
  forever. It is now `start_consumer(run_forever=True)` with a `stop_event`, and the tests call
  `run_forever=False`.
- **Bug fixed: embedding failures.** A failed embedding no longer affects moderation. All-zero
  vectors are no longer sent; they gave NaN cosine distances in pgvector.
- **New endpoints and CORS.** `GET /stats` and `POST /embed` were added, plus CORS so the UI can
  read `/stats`.
- **Dependency pins.** `kafka-python==2.3.2` (2.0.2 breaks on Python ≥ 3.12); unpinned numpy and
  torch removed.
- **New tests:** `app/tests/test_llm_and_embedder.py`. `conftest.py` disables Kafka and the LLM
  during tests.

### Backend (`backend/`) ⚠️ not yet compiled — verify first
1. **Moderation flag never saved.** In `ModerationResultEvent`, Lombok makes a `boolean isModerated`
   field map to the JSON property `"moderated"`, but Python sends `"isModerated"`, so the flag was
   always false. Fixed with `@Getter/@Setter(onMethod_ = @JsonProperty("isModerated"))`. The
   `isModerated()`/`setModerated()` API is kept for the tests. Also added `maskedTitle`,
   `categories`, `severity`, `reason`, `provider`, and `@JsonIgnoreProperties(ignoreUnknown = true)`.
2. **Embedding consumer used the wrong deserializer.** It used the ModerationResultEvent factory,
   so every embedding message failed. `KafkaConfig` now has `embeddingConsumerFactory` +
   `embeddingListenerContainerFactory`, and `EmbeddingConsumer` uses it.
3. **Embeddings broke reads.** `Post.embedding` (`float[]` ↔ `vector(384)`) could be written but
   not read back, so feeds would crash once embeddings existed. Added the `hibernate-vector`
   dependency (`${hibernate.version}`) plus `@JdbcTypeCode(SqlTypes.VECTOR) @Array(length = 384)`.
   ⚠️ Verify that `ddl-auto=validate` accepts it, and that `findSimilarPosts` (native
   `SELECT p.*`) still maps.
4. **Race on new posts.** `PostService.createPost` is now `@Transactional`, and the Kafka event is
   published in `afterCommit` (immediately when no transaction is active, as in unit tests).
   `handleModerationResult` also applies `maskedTitle`.
5. **Admin endpoints always 403.** `JwtAuthenticationFilter` granted only permissions, so
   `hasRole('ADMIN')` could never pass. It now also grants `ROLE_<role>`.
6. **Recommended feed threw an NPE.** In `SecurityConfig`, `GET /api/posts/recommended` was matched
   by the earlier `GET /api/posts/*` permitAll, so it ran anonymously. The matcher is moved above it.
7. **Healthcheck endpoint missing.** The Docker healthcheck called `/api/health`, which didn't exist
   and wasn't permitted. Added `HealthController#publicHealth` (`{"status":"UP"}`) and permitAll.
   Static UI files are also permitted.
8. **`PostResponse.moderated` added** and mapped in `PostMapper`, for the UI badge.
9. **`PostCreatedEvent.title` added.** The 3-arg constructor is kept. `PostProducer` keeps the
   2-arg `send(topic, event)` that `PostProducerTest` verifies.
10. **`application.properties` was gitignored**, so a fresh clone could not build. It is now
    un-ignored, `show-sql` is off, and `missing-topics-fatal=false` is set.
11. **Dockerfile:** builds with the `maven:3.9.9-eclipse-temurin-21` image (Windows CRLF `mvnw`
    breaks `./mvnw` in Linux) and installs curl for the healthcheck.

### Infra
- `docker-compose.yml`: added a **mailpit** service (SMTP 1025, UI http://localhost:8025),
  `SPRING_MAIL_HOST=mailpit`, and Groq env vars for ml-service (`GROQ_API_KEY`, `LLM_MODEL`,
  `LLM_BASE_URL` from `.env`).
- `.env.example` added. Copy it to `.env` and put the Groq key there. **Never commit `.env`.**
- Root `.gitignore`: ignores `.env`, `.transfer/`, `*.log`.

### Frontend ✅ syntax-checked (not yet clicked through in a browser)
- `backend/src/main/resources/static/{index.html,styles.css,app.js}`: a vanilla-JS UI served by
  Spring at http://localhost:8080/ (no npm, no CORS issues).
- Features: sign-up with auto email-verify (dev flag), login with token refresh, global/home feed
  with sort, create post, live "checking… → moderated" badge, votes, nested comments, create/join
  community, and a status panel for the API and ML service.

## Checklist for Claude Code

```powershell
# 0. Clean up the transfer folder left by the cloud session (~1 GB)
Remove-Item -Recurse -Force D:\Reddit_clone\.transfer

# 1. Backend compiles + tests (Docker Desktop must be running for *IT tests)
cd D:\Reddit_clone\backend
.\mvnw.cmd -q clean test      # or: mvn clean test

# 2. ML service tests
cd ..\ml-service
python -m venv venv; .\venv\Scripts\activate; pip install -r requirements.txt
python -m pytest -q

# 3. Full stack
cd ..
copy .env.example .env         # add GROQ_API_KEY (optional)
docker compose up -d --build
docker compose ps              # all healthy?
docker compose logs -f ml-service backend
```

**End-to-end smoke test** (PowerShell):
```powershell
$u = Invoke-RestMethod -Method Post http://localhost:8080/api/users -ContentType 'application/json' -Body '{"username":"demo1","email":"demo1@x.com","password":"Password@123"}'
Invoke-RestMethod -Method Post "http://localhost:8080/api/auth/verify?token=$($u.verificationToken)"
$t = Invoke-RestMethod -Method Post http://localhost:8080/api/auth/login -ContentType 'application/json' -Body '{"email":"demo1@x.com","password":"Password@123"}'
$h = @{Authorization = "Bearer $($t.access_token)"}
$p = Invoke-RestMethod -Method Post http://localhost:8080/api/posts -Headers $h -ContentType 'application/json' -Body '{"title":"hello","text":"what the fuck is this"}'
Start-Sleep 5
Invoke-RestMethod http://localhost:8080/api/posts/$($p.id)     # expect text "what the **** is this", moderated=True
docker exec -it reddit_clone-postgres-1 psql -U postgres -d reddit_clone -c "select id, is_moderated, display_text, embedding is not null as has_emb from posts order by id desc limit 3;"
```
(Check the password rules in `CreateUserRequest` if registration is rejected.)

## Known follow-ups / nice-to-haves
- Comments are not sent through the moderation pipeline yet. `comments.is_moderated` exists, so
  this is the obvious next step: publish `type: "COMMENT"` and route results by type.
- The `moderation_flags` table (V1) is unused. Persist `categories`, `severity` and `reason` there
  when `isModerated` is true.
- ~~Add an `isModerated` JSON regression test~~ Done: `ModerationResultEventJsonTest`.
- ~~`AuthService.login` doesn't check `isVerified`~~ Done: it now rejects unverified users, as the ITs expect.
- Don't edit already-applied Flyway migrations (V1–V16). Add V17+ for schema changes.
