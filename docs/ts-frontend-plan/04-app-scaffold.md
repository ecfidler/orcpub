# Phase 3 — TypeScript App Scaffold

Goal: a running `web-ts/` app with routing, auth, the API client, and the
engine package wired in — proven by a working login → character list flow
against the real backend.

## Stack choices

| Concern | Choice | Rationale |
|---------|--------|-----------|
| Build | Vite | fast, zero-config TS/React |
| UI | React 18 + TypeScript | the cljs app already renders through React (Reagent), so the mental model transfers |
| Routing | react-router | route table mirrors `route_map.cljc` page routes |
| Server state | TanStack Query | caching/retries for the API endpoints; replaces the ad-hoc ajax in `events.cljs` |
| Client state | Zustand (or Redux Toolkit) | one store: current entity, auth token, UI state |
| Wire | transit-js codec (doc 02) | |
| Rules | `@dmv/pubdoor` (doc 03) | async-loaded chunk |
| Tests | vitest + Playwright | golden files + a few E2E flows |

Redux Toolkit is conceptually closest to re-frame if you ever need to
cross-reference the old code (`events.cljs` ≈ actions/reducers, `subs.cljs` ≈
selectors, `views.cljs` ≈ components), but Zustand is less ceremony; either
works.

## Project layout

```
web-ts/
  src/
    api/          # transit codec + typed endpoint functions (doc 02)
    engine/       # thin wrapper around @dmv/pubdoor: async load, debounced build
    state/        # store: auth slice, character slice, homebrew slice
    routes/       # route table; one dir per page (doc 05)
    components/   # shared UI (option cards, ability blocks, modals)
    styles/       # see "Styling" below
  fixtures/       # Phase 0 captures
  e2e/
```

## Dev-server proxy & CORS

Run the Clojure backend as-is on :8890 and put Vite's dev proxy in front
(`/dnd`, `/login`, `/register`, `/user`, `/character.pdf`, `/check-*` →
localhost:8890). The dev backend already allows all origins
(`system.clj`), but the proxy keeps everything same-origin so cookies
(password reset) and CSP behave like production.

For production, the simplest deployment is unchanged: serve the built
`web-ts/dist/` from any static host (or from the backend's public resources
dir) with the API on the same origin.

## Auth slice

- `POST /login` → store token (memory + localStorage), decode nothing — treat
  the JWT as opaque
- attach `Authorization: Token <jwt>` in the API client
- global 401 handler → clear token, redirect to login
- registration flow: `check-username`/`check-email` live-validation like the
  current form, `POST /register`, then the "verification email sent" screen

## Character state model

The central design decision. Recommended:

```
store.character = {
  entity: StrictEntity,          // single source of truth, opaque
  built: BuiltCharacter | null,  // derived, debounced
  selections: AvailableSelection[], // derived, debounced
  dirty: boolean,
}
```

Every UI mutation goes through `engine.selectOption`/`setValue` producing a
new `entity`; a debounced effect (~150ms trailing, like the cljs app's
debounced `entity/build` in `subs.cljs`) recomputes `built` and `selections`.
Saving posts `entity` verbatim to `POST /dnd/5e/characters`; loading sets it
verbatim from `GET /dnd/5e/characters/:id`. The strict entity is never
interpreted by TS code — only the engine reads it.

Autosave: the current app autosaves drafts to localStorage
(`autosave_fx.cljs`); replicate with a persisted-store middleware.

## Styling

The current CSS is generated Garden (`src/clj/orcpub/styles/core.clj`) plus
static files in `resources/public/css`. Don't port it. Pick Tailwind (or CSS
modules) and restyle from the rendered reference app — this is a rewrite's
one free win. Copy static assets (logo, icons, fonts in `resources/`) as
needed; note font/asset licenses.

## Milestone for this phase (M1 — "read-only client")

Ship, in order:

1. Scaffold + proxy + transit codec round-tripping fixtures
2. Login page → token stored → `GET /user` shown
3. Character list page from `GET /dnd/5e/character-summaries`
4. Read-only character sheet: `GET /dnd/5e/characters/:id` →
   `engine.buildCharacter` → rendered sheet matching the reference app for
   the golden characters

Step 4 forces every risky integration (Transit, auth, engine boundary,
bundle loading) through end-to-end before any builder work starts.

## Exit criteria

- [ ] Login/logout/registration against the real backend
- [ ] Character list renders for the test user
- [ ] Read-only sheet renders golden characters with matching computed values
- [ ] CI: typecheck, vitest (codec + engine goldens), one Playwright smoke test
