# Plan Set 1: a TypeScript frontend on the compiled engine (Option A)

> Status: reference. The plan under consideration is
> `docs/ts-rewrite-plan/` (Plan Set 2), which builds on this one. Read this
> set for the engine facade, app scaffold, and API details that Plan Set 2
> references. Linear tracks the work itself, in the projects PubDoor and
> Alchemy 5e.

This plan replaces the ClojureScript and re-frame frontend with a
TypeScript and React frontend. It reuses the existing rules engine as a
compiled npm library and keeps the existing Clojure backend unchanged.

## Why this strategy

In this codebase the frontend is more than UI. The browser runs the entire
D&D 5e rules engine: the shared `.cljc` code in `src/cljc/orcpub/`, which
holds the template tree, the entity and choice model, the modifiers, and
about 2 MB of game data. The backend (`src/clj/orcpub/routes.clj`) is a
thin persistence and auth layer. It stores only the raw choice tree
(`src/cljc/orcpub/entity/strict.cljc`) and never computes a character
sheet.

Rewriting the UI is a large but tractable project. Rewriting the rules
engine at the same time is not. So the plan is:

- **Reuse the engine.** Compile the `.cljc` engine to JavaScript with
  shadow-cljs (`:npm-module` target) behind a small, typed facade. This
  gives full rules fidelity and `.orcbrew` homebrew compatibility with
  almost no ongoing Clojure work.
- **Keep the backend.** The Pedestal backend stays as it is. Its character
  spec is game-agnostic, its auth is standard JWT, and the Transit wire
  format has an official JS client, `transit-js`, so a TypeScript client
  can talk to it directly.
- **Rewrite the UI only.** The UI layer (`src/cljs/orcpub/`) is rewritten
  in TypeScript and React.

If the engine ever needs replacing, it can be swapped out step by step
behind the same TypeScript interface. That converts Option A into a full
rewrite on your own schedule, without a single risky cutover.

## Plan documents

See also `docs/ts-rewrite-plan/` (Plan Set 2): a new application built on
this same compiled-engine approach, with its own backend and user-level
import of `.orcbrew` files and characters from the old app. It references
these documents where they apply.

| Doc | Phase | Summary |
|-----|-------|---------|
| [01-reference-app.md](01-reference-app.md) | 0 | Run the existing app and capture reference behavior to test against |
| [02-api-surface.md](02-api-surface.md) | 1 | The complete backend API map: endpoints, auth, and the Transit wire format |
| [03-engine-package.md](03-engine-package.md) | 2 | Compile the rules engine to an npm package with a typed facade |
| [04-app-scaffold.md](04-app-scaffold.md) | 3 | Scaffold the Vite, React, and TypeScript app, the API client, and state management |
| [05-page-rebuild.md](05-page-rebuild.md) | 4 | Rebuild pages in dependency order, mapped to their ClojureScript sources |
| [06-pdf-and-orcbrew.md](06-pdf-and-orcbrew.md) | 5 | PDF export and `.orcbrew` homebrew import |
| [07-milestones-and-risks.md](07-milestones-and-risks.md) | all | Milestone sequencing, definitions of done, and the risk register |

## Ground rules

- The new frontend is in its own directory, suggested `web-ts/` at the
  repo root, or its own repository. It does not touch `src/clj` or
  `src/cljc`.
- The current frontend keeps working throughout. Both clients can run
  against the same backend during the transition.
- Every milestone ends with something usable against the real backend.
  There is no months-long period with nothing to show.

## The one Clojure exception

The engine facade (phase 2) requires a few hundred lines of Clojure:
mechanical glue that exposes existing functions, not game logic.
Everything after that is TypeScript.
