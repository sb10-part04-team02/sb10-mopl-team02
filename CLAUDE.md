# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

**모두의 플리 (Mopl)** — a backend for a movie/TV/sports content rating + curation platform with real-time "watch-together" social features (co-watching, content chat, DMs, follows, notifications). Spring Boot REST API.

> The authoritative contracts are `ref/api-docs-codeit-mopl.json` (OpenAPI spec) and the conventions in `.coderabbit.yml`.

## Working principles

Bias toward caution over speed; for trivial tasks, use judgment.

- **Think first.** State assumptions explicitly; if uncertain or multiple interpretations exist, ask instead of picking silently. If a simpler approach exists, say so and push back when warranted.
- **Simplest thing that works.** No features, abstractions, configurability, or error handling beyond what was asked. If you write 200 lines and it could be 50, rewrite it.
- **Surgical edits.** Every changed line should trace to the request. Don't refactor or reformat adjacent code; match existing style. Remove only the imports/variables your change orphaned; flag pre-existing dead code instead of deleting it.
- **Verify against a goal.** Turn the task into a checkable outcome ("fix the bug" -> write a failing test, then make it pass). For multi-step work, state a brief plan with a verify step for each step.

## Commands

```bash
./gradlew build              # compile + test + assemble
./gradlew bootRun            # run the app (default profile)
./gradlew test               # run all tests (JUnit 5)
./gradlew test --tests 'com.team02.mopl.SomeTest'          # single test class
./gradlew test --tests 'com.team02.mopl.SomeTest.method'   # single test method
./gradlew clean build
```

- Java 17 (toolchain enforced), Spring Boot 3.5.x, Gradle wrapper.
- Base package: `com.team02.mopl`.

## Profiles & Database

- **dev**: 실제 DB(PostgreSQL)를 사용. **test**: Testcontainers로 PostgreSQL 컨테이너를 띄워 실행.
- **prod**: PostgreSQL (`runtimeOnly org.postgresql`).
- Config lives in `src/main/resources/application.yml` plus `application-dev.yml` / `application-prod.yml`. Never read or write `.env` files.

## Architecture & domain conventions

These come from `.coderabbit.yml` (which CodeRabbit enforces on every PR) and the OpenAPI spec. Follow them when adding code — they are the project's contract.

**Layering**: Controller -> Service -> Repository. Controllers hold no business logic and never expose entities (always map to DTOs). Services own `@Transactional` boundaries (`readOnly = true` for queries) and ownership/role checks.

**Identifiers & time**: every entity PK is a **UUID**. Timestamps are ISO-8601; auto-manage `createdAt`/`updatedAt` via `@CreatedDate`/`@LastModifiedDate`. Associations default to `LAZY`.

**Auth/security**:
- JWT Bearer (`Authorization: Bearer <accessToken>`); roles `USER` / `ADMIN`. Stateless/distributed-friendly.
- CSRF on mutations (POST/PATCH/DELETE): token in cookie `XSRF-TOKEN`, sent back in header `X-XSRF-TOKEN`.
- Admin account auto-initialized at startup. Role change or account lock force-logs-out that user; locked accounts can't sign in.
- Password reset issues a temp password expiring in 3 minutes, reuses the normal sign-in API, and destroys the temp password on reset.

**Cursor pagination** (shared): request params `cursor`, `idAfter` (uuid), `limit`, `sortDirection` (`ASCENDING`|`DESCENDING`), `sortBy`. Response `CursorResponse`: `data / nextCursor / nextIdAfter / hasNext / totalCount / sortBy / sortDirection`. Repository queries must use composite (sortKey, id) comparison for stable paging.

**Errors**: global `@RestControllerAdvice` returns `ErrorResponse { exceptionName, message, details: Map<field,message> }`. Use domain-specific custom exceptions, not raw `RuntimeException`. Status codes per spec: 200/201, 204, 400, 401, 403, 404, 500. Never leak stack traces.

**Domain rules**:
- Content `type` enum: `MOVIE | TV_SERIES | SPORT`. CRUD is ADMIN-only. Data ingested from TMDB (movie/tv) and The Sports DB (sport) via **Spring Batch** (idempotent, chunked). Sort: `createdAt | watcherCount | rate`.
- Reviews: only the author may edit/delete (else 403); `rating` is a double; required `contentId / text / rating`. Sort: `createdAt | rating`.
- Playlists: only the owner may modify or add/remove contents (else 403); subscribable; adding content to a subscribed playlist notifies subscribers. Responses include requester-relative `subscribedByMe`. Sort: `updatedAt | subscribeCount`.
- Follows are one-directional; a new follower triggers a notification.
- Notification `level` enum: `INFO | WARNING | ERROR`. Triggers: role change, your-playlist subscription, content added to a subscribed playlist, followed-user activity, new follower, DM received.

**Real-time**:
- WebSocket/STOMP at `/ws` (authenticated via access token in the handshake header) for co-watching, content chat, and DMs. Destinations: `/sub/contents/{id}/watch`, `/sub|/pub /contents/{id}/chat`, `/sub|/pub /conversations/{id}/direct-messages`. **Content chat messages are not persisted** (WebSocket only).
- SSE at `/api/sse` delivers notifications and inactive-conversation DMs. Event names: `notifications`, `direct-messages`.

## Workflow conventions

- Branch naming: `feat/#<issue>/<slug>` (e.g. `feat/#1/coderabbitai`). PRs target `dev`; `main` is the release branch.
- **CodeRabbit** auto-reviews PRs into `main`/`dev` in Korean (`.coderabbit.yml`, `assertive` profile). Treat its `path_instructions` as the canonical per-layer checklist.
- Closing a PR fires a Discord notification via `.github/workflows/pr-discord-notify.yml`.
- PR/issue templates and team docs are in `.github/` and `docs/`; commit messages in this repo are Korean and Conventional-Commit style (`feat:`, `fix:`, `chore:`, `docs:`).

## ref/ directory

`ref/` is gitignored reference material, not part of the build: the frontend project (`project-mopl-fe-1.0.2`), the OpenAPI spec (`api-docs-codeit-mopl.json`), and project guides. Use it to understand intended API shapes and frontend expectations; do not import or depend on it.
