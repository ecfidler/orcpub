# TypeScript Frontend Rewrite — Plan (Option A)

> **Status: reference.** The plan under active consideration is
> `docs/ts-rewrite-plan/` (Plan Set 2), which builds on this one. Read this
> set for the engine facade, app scaffold, and API details it references.

A plan for replacing the ClojureScript/re-frame frontend with a modern
TypeScript + React frontend, while **reusing the existing Clojure(Script) rules
engine as a compiled npm library** and **keeping the existing Clojure backend
unchanged**.

## Why this strategy

In this codebase the frontend is not just UI. The browser runs the entire D&D
5e rules engine — the shared `.cljc` code in `src/cljc/orcpub/` (template tree,
entity/choice model, modifiers, and ~2MB of game data). The backend
(`src/clj/orcpub/routes.clj`) is a thin persistence/auth layer: it stores only
the raw choice tree (`src/cljc/orcpub/entity/strict.cljc`) and never computes a
character sheet.

Rewriting the UI is a big but tractable project. Rewriting the rules engine at
the same time is not. So:

- **Reuse**: compile the `.cljc` engine to JavaScript with shadow-cljs
  (`:npm-module` target) behind a small, typed facade. 100% rules fidelity and
  free `.orcbrew` homebrew compatibility, with almost no ongoing Clojure work.
- **Keep**: the Pedestal backend as-is. Its character spec is game-agnostic,
  auth is standard JWT, and the Transit wire format has an official JS client
  (`transit-js`), so a TypeScript client can talk to it natively.
- **Rewrite**: only the UI layer (`src/cljs/orcpub/`) in TypeScript/React.

If the engine ever needs to be replaced, it can be swapped incrementally behind
the same TypeScript interface (this converts Option A into a full rewrite on
your own schedule, without a big-bang risk).

## Plan documents

See also `docs/ts-rewrite-plan/` (Plan Set 2): a new application built on
this same compiled-engine approach, with its own backend and user-level
import of `.orcbrew` files and characters from the old app. It references
these documents where they apply.

| Doc | Phase | Summary |
|-----|-------|---------|
| [01-reference-app.md](01-reference-app.md) | 0 | Run the existing app; capture reference behavior to test against |
| [02-api-surface.md](02-api-surface.md) | 1 | The complete backend API map: endpoints, auth, Transit wire format |
| [03-engine-package.md](03-engine-package.md) | 2 | Compile the rules engine to an npm package with a typed facade |
| [04-app-scaffold.md](04-app-scaffold.md) | 3 | Scaffold the Vite + React + TS app; API client; state management |
| [05-page-rebuild.md](05-page-rebuild.md) | 4 | Rebuild pages in dependency order, mapped to their cljs sources |
| [06-pdf-and-orcbrew.md](06-pdf-and-orcbrew.md) | 5 | PDF export and `.orcbrew` homebrew import |
| [07-milestones-and-risks.md](07-milestones-and-risks.md) | — | Milestone sequencing, definitions of done, risk register |

## Ground rules

- The new frontend lives in its own directory (suggested: `web-ts/` at the repo
  root) or its own repository. It does not touch `src/clj` or `src/cljc`.
- The old frontend keeps working throughout; both clients can run against the
  same backend during the transition.
- Every milestone ends with something usable against the real backend — no
  months-long dark period.

## The one Clojure exception

The engine facade (phase 2) requires writing a few hundred lines of Clojure —
mechanical glue exposing existing functions, not game logic. Everything after
that is TypeScript.
