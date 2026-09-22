# 05: Application and backend

The application layer is mostly the same work as Plan Set 1, so this
document points at those docs and lists what differs for a new product. It
then covers the new backend, which is designed independently of orcpub.

## Reuse from Plan Set 1

- **Stack and scaffold.** `docs/ts-frontend-plan/04-app-scaffold.md`: Vite,
  React, TypeScript, TanStack Query, Zustand or Redux Toolkit, vitest, and
  Playwright. Unchanged, except that there is no dev proxy to an old
  server.
- **State model.** The same doc. The strict entity is the single source of
  truth, and `evaluate` produces the derived state. With a memoized
  `evaluate` (doc 02), the old 500 ms debounce is unnecessary.
- **Page rebuild order.** `docs/ts-frontend-plan/05-page-rebuild.md`. The
  tiers and the six builder sub-milestones hold. Drop 4.4 (PDF). The
  homebrew builders are doc 04 here.
- **Engine facade.** `docs/ts-frontend-plan/03-engine-package.md` is the
  base. Doc 02 here is the delta.
- **Reference capture.** `docs/ts-frontend-plan/01-reference-app.md` covers
  running the old app and collecting fixtures.

## What differs

- **No API client to the old backend.** Plan Set 1 doc 02 is useful only as
  documentation of the strict-entity payload and of the endpoints the
  exporter bookmarklet calls (doc 03).
- **Local-first is the first mode, not an option.** Characters and homebrew
  are in IndexedDB, files import and export, and no account is needed. The
  Playwright suite runs entirely in this mode. This is also how a user
  evaluates the new app before committing to it.
- **Import and export are top-level features.** "Import from Dungeon
  Master's Vault" accepts the bookmarklet bundle, an `.orcbrew` file, or a
  single character URL or file. "Export everything" writes the `dmv-export`
  bundle and `all-content.orcbrew`.
- **Routing** need not mirror the old URLs, because the old app is not
  being replaced in place. Keep the spell, monster, and item pages keyed by
  content key so that links can be shared.
- **No PDF, Orcacle, newb builder, or combat tracker** unless they are
  wanted later.

## The new backend

The backend is designed for the new app. Nothing about orcpub's storage,
Transit API, or Datomic schema is carried over. It must meet these
requirements:

- **Accounts.** Email and password with verification and password reset,
  or OIDC.
- **Characters.** Opaque `dmv-character` documents per user (doc 03), with
  a summary projection for lists. Sharing by link, that is, public read of
  one character, which is the one old-app feature users rely on.
- **Homebrew.** Per-user packs, one document per pack holding the
  multi-plugin map, so a user's content follows them across devices.
  Optional public or shared packs later.
- **Parties and folders.** Small. Mirror the old feature set from the UI's
  point of view.
- **Sync model.** Either local-first with sync, where IndexedDB is
  canonical offline and the server reconciles on login, or plain
  server-authoritative if sync is more than you want. Decide before M5.
- **Stack.** Anything TypeScript-native, for example Hono or Fastify with
  Postgres and JSONB, or a hosted backend-as-a-service. The contract with
  the frontend is a JSON API and the two document formats.

Because the frontend works fully local-first, the backend can arrive late
without blocking anything above it.

## Milestone alignment

See doc 06. The app milestones, in order, are the local-first viewer, the
builder, homebrew, import tooling, and the backend.
