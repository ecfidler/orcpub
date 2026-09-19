# 08 — Application and UI

The application layer is mostly the same work as Plan Set 1, so this
document is short: it points at those docs and lists what changes when the
engine is TypeScript rather than compiled ClojureScript.

## Reuse from Plan Set 1

- **Stack and scaffold** — `docs/ts-frontend-plan/04-app-scaffold.md`:
  Vite + React + TypeScript, TanStack Query for server state, Zustand (or
  Redux Toolkit) for client state, vitest + Playwright. All still right.
- **Page rebuild order** — `docs/ts-frontend-plan/05-page-rebuild.md`: the
  four tiers (core loop → organization → content browsing → homebrew
  builders) and the six builder sub-milestones are unchanged; only the
  engine underneath differs.
- **Reference capture** — `docs/ts-frontend-plan/01-reference-app.md`:
  running the old app, capturing API traffic and golden characters. Extend
  the golden set as doc 04 §"Golden tests" describes.
- **API surface** — `docs/ts-frontend-plan/02-api-surface.md` is the
  adapter's spec (doc 05).

## What changes

**No engine chunk, no boundary conversion.** The engine is ordinary
TypeScript in the same workspace (`packages/engine`), so there's no async
loading, no `clj->js`, no hand-written `.d.ts`, and builds are fast enough
that the builder can recompute on every keystroke without the old 500 ms
debounce.

**Content is data the UI can render directly.** Spell/monster/item browse
pages read `content/srd/*.json` (code-split per type) and the homebrew
registry; there is no "expose a search function from the engine" step.

**The builder is driven by `evaluate(entity)`.** The UI holds a strict
entity in the store; every interaction calls a pure mutation from
`packages/engine`, then `evaluate` returns `{ built, selections }`. Panes are
rendered from `selections` (tags route them to builder pages, as `::t/tags`
did); the sheet preview from `built`. This is the same shape as Plan Set 1's
state model, with the facade replaced by direct calls.

**Homebrew builders are schema-driven forms.** Because every content type
has a JSON Schema (doc 02), the ~14 homebrew builders can be generated from
it (react-jsonschema-form or a hand-rolled schema renderer) with per-type
customization for the mechanics editors (`props` pickers,
`level-modifiers` tables, spellcasting config). This collapses most of
Tier 4 of the page plan into one generic builder plus polish.

**Local-first is a first-class mode** (doc 05): the app must be fully usable
with `LocalBackend` — characters and homebrew in IndexedDB, file
import/export — before any server is involved. The Playwright suite runs
in this mode, with the `OrcpubBackend` covered by a smaller integration
suite against a dockerized old server.

**Routing keeps the old URLs** (`/pages/dnd/5e/characters/:id`, spell/monster
pages by key) so shared links keep working when an operator swaps
frontends; `route_map.cljc` is the list.

## Milestone sequence for the app layer

Aligned with doc 09; the app can't render a sheet before the engine
evaluates one, so the app milestones trail the engine milestones by one:

1. **Local-first viewer**: import a strict-entity JSON (or a Phase-0
   capture), render the read-only sheet from `evaluate` — first visible
   proof the engine works.
2. **Drop-in frontend**: login and character list/sheet against an old
   server through the adapter; PDF via the adapter.
3. **Builder**: the six sub-milestones from Plan Set 1 doc 05 §4.3, now
   with engine-native selections.
4. **Homebrew**: import/export, schema-driven builders, conflicts.
5. **Everything else**: parties/folders/account, browse pages, spell cards.
