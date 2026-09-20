# CLAUDE.md

Guidance for AI agents working in this repository.

## What this repository is

Dungeon Master's Vault (a fork of OrcPub2): a Clojure/ClojureScript D&D 5e
character builder. Backend is Pedestal + Datomic (`src/clj`), frontend is
Reagent/re-frame (`src/cljs`), and the rules engine plus all game content is
shared `.cljc` code (`src/cljc`). See `README.md` and `docs/STACK.md`.

The single most important architectural fact: **the rules engine runs in
the browser.** The backend stores only a character's raw choice tree (the
"strict entity", `src/cljc/orcpub/entity/strict.cljc`); everything computed
(AC, spell slots, available options) comes from `src/cljc/orcpub/entity.cljc`,
`template.cljc`, `modifiers.cljc`, and `dnd/e5/template_base.cljc`.

## Planning documents — read these first for any frontend/rewrite work

Two plan sets live under `docs/` and describe the intended direction for a
TypeScript frontend. They are grounded in an investigation of this codebase
and cite file and line numbers; prefer them over re-deriving the
architecture.

- **`docs/ts-frontend-plan/`** (Plan Set 1) — replace the ClojureScript UI
  with TypeScript/React while **reusing the compiled `.cljc` engine as an
  npm library** and keeping this backend unchanged. Start at its `README.md`.
  Key docs: `02-api-surface.md` (every backend endpoint, Transit wire format,
  JWT auth) and `03-engine-package.md` (shadow-cljs facade design).
- **`docs/ts-rewrite-plan/`** (Plan Set 2) — a **new application** built on
  that same compiled engine, with its own backend and **user-level
  compatibility**: users import `.orcbrew` homebrew files and characters
  exported from this app. Start at its `README.md`. Key docs:
  `01-compatibility-contract.md` (what must import/export, with the legacy
  data quirks) and `02-engine-library.md` (facade surface, engine wrinkles,
  the few upstream patches needed).

Plan Set 2 references Plan Set 1 where the work is identical; read Plan
Set 1 first if you're new to either.

## Facts the plans depend on (don't contradict them without checking)

- Shipped game content is **SRD 5.1 only**. Non-SRD content exists in the
  source but is reader-discarded (`#_`) or unreferenced (`templates/`).
  Do not enable it.
- Built-in races, backgrounds, and languages are defined in
  `src/cljs/orcpub/dnd/e5/spell_subs.cljs`, not in `src/cljc`.
- Content is referenced by keyword key everywhere (saved characters,
  `.orcbrew` files). Key derivation is `common/name-to-kw`; 16 spells carry
  explicit keys. Changing keys breaks saved data.
- `.orcbrew` files are EDN; the import pipeline is
  `src/cljs/orcpub/dnd/e5/import_validation.cljs`; the format and its known
  drift are documented in `docs/ORCBREW_FILE_VALIDATION.md` and
  `docs/ts-rewrite-plan/01-compatibility-contract.md`.
- Selection order in a strict entity is semantically significant
  (`entity.cljc` uses `array-map` deliberately).

## Working in this repo

- Dev setup: `docs/GETTING-STARTED.md`; Docker: `docs/DOCKER.md`;
  environment variables: `docs/ENVIRONMENT.md`.
- Tests: `lein test`. Engine golden-test candidates are
  `test/cljc/orcpub/dnd/e5/warlock_test.clj` and the round-trip entities in
  `character_test.clj`.
- Large files: `views.cljs` (339 KB) and `events.cljs` (156 KB) — search
  for the keyword you need rather than reading top to bottom.
- Do not edit the plan documents' cited line numbers by hand; regenerate
  them from the source if the code moves.
