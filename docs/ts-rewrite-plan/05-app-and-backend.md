# 05 — Application and Backend

The application layer is mostly the same work as Plan Set 1, so this
document points at those docs and lists what differs for a new product.
Then it covers the new backend, which is designed independently of orcpub.

## Reuse from Plan Set 1

- **Stack and scaffold** — `docs/ts-frontend-plan/04-app-scaffold.md`: Vite +
  React + TypeScript, TanStack Query, Zustand (or Redux Toolkit), vitest +
  Playwright. Unchanged, minus the dev proxy to an old server.
- **State model** — same doc: the strict entity is the single source of
  truth; `evaluate` produces derived state. With memoized `evaluate` (doc 02)
  the old 500 ms debounce is unnecessary.
- **Page rebuild order** — `docs/ts-frontend-plan/05-page-rebuild.md`: the
  tiers and the six builder sub-milestones hold. Drop 4.4 (PDF). The
  homebrew builders are doc 04 here.
- **Engine facade** — `docs/ts-frontend-plan/03-engine-package.md` is the
  base; doc 02 here is the delta.
- **Reference capture** — `docs/ts-frontend-plan/01-reference-app.md` for
  running the old app and collecting fixtures.

## What differs

- **No API client to the old backend.** Plan Set 1 doc 02 is useful only as
  documentation of the strict-entity payload and of the endpoints the
  exporter bookmarklet calls (doc 03).
- **Local-first is the first mode**, not an option: characters and homebrew
  in IndexedDB, file import/export, no account needed. The Playwright suite
  runs entirely in this mode. This is also how a user evaluates the new app
  before committing to it.
- **Import/export is a top-level feature**: "Import from Dungeon Master's
  Vault" (bookmarklet bundle, `.orcbrew`, single character URL/file),
  "Export everything" (the `dmv-export` bundle + `all-content.orcbrew`).
- **Routing** need not mirror the old URLs; the old app isn't being replaced
  in place. Keep spell/monster/item pages keyed by content key for
  shareability.
- **No PDF, no Orcacle, no newb builder, no combat tracker** unless wanted
  later.

## The new backend

Designed for the new app; nothing about orcpub's storage, Transit API, or
Datomic schema is carried over. Requirements it must meet:

- **Accounts**: email/password + verification, password reset, or OIDC.
- **Characters**: opaque `dmv-character` documents per user (doc 03) with a
  summary projection for lists; sharing by link (public read of one
  character, the one old-app feature users rely on).
- **Homebrew**: per-user packs (the multi-plugin map, one document per pack)
  so a user's content follows them across devices; optional public/shared
  packs later.
- **Parties / folders**: small, mirror the old feature set from the UI's
  point of view.
- **Sync model**: local-first with sync (IndexedDB is canonical offline; the
  server reconciles on login) — or simple server-authoritative if sync is
  more than you want. Decide before M5.
- **Stack**: anything TypeScript-native (e.g. Hono/Fastify + Postgres with
  JSONB, or a hosted BaaS). The contract with the frontend is a JSON API
  and the two document formats.

Because the frontend works fully local-first, the backend can arrive late
without blocking anything above it.

## Milestone alignment

See doc 06. The app milestones are: local-first viewer → builder → homebrew
→ import tooling → backend.
