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

**Tracking lives in Linear** (workspace *Orc Alchemy*,
https://linear.app/orc-alchemy):

- **PubDoor** — the engine package, Phase A, this fork:
  https://linear.app/orc-alchemy/project/pubdoor-13db9e66264b
- **Alchemy 5e** — the new app, Phase B, its own repository:
  https://linear.app/orc-alchemy/project/alchemy-5e-9db66f3ef51e
- *Plan overview* (team document; links the compatibility contract and
  the risk register):
  https://linear.app/orc-alchemy/document/plan-overview-typescript-rewrite-on-the-compiled-engine-1afe72ecb90d

Milestones, issue status and sequencing are there; the documents below are
the technical reference. When they disagree on *what* is built, the docs
win; on *status*, Linear wins. Reference the issue (`ORC-nn`) in commits
and PRs.

**Status: `docs/ts-rewrite-plan/` (Plan Set 2) is the plan being seriously
considered for implementation.** When the user says "the plan" without
qualification, they mean Plan Set 2. Plan Set 1 remains as reference
material that Plan Set 2 builds on.

- **`docs/ts-rewrite-plan/`** (Plan Set 2, **active**) — a **new
  application** built on the compiled `.cljc` engine as an npm library, with
  its own backend and **user-level compatibility**: users import `.orcbrew`
  homebrew files and characters exported from this app. Start at its
  `README.md`. Key docs: `01-compatibility-contract.md` (what must
  import/export, with the legacy data quirks) and `02-engine-library.md`
  (facade surface, engine wrinkles, the few upstream patches needed).
  **Implementing Phase A (the engine package, in this fork)? Read
  `docs/ts-rewrite-plan/HANDOFF-phase-a.md` first.** M0 (fixtures) is done:
  `fixtures/` and `scripts/` are the oracle outputs and tooling, described
  in `fixtures/README.md`, whose *Findings* section corrects the plan in a
  few places.
- **`docs/ts-frontend-plan/`** (Plan Set 1, reference) — replace the
  ClojureScript UI with TypeScript/React while reusing the compiled engine
  and keeping this backend unchanged. Plan Set 2 references it where the
  work is identical (engine facade, app scaffold, page order). Key docs:
  `02-api-surface.md` (every backend endpoint, Transit wire format, JWT
  auth) and `03-engine-package.md` (shadow-cljs facade design).

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

## Agent skills

Matt Pocock's engineering and productivity skills are installed as project
skills under `.claude/skills/` (tracked by `skills-lock.json`; refresh with
`npx skills@latest update`). Start with `/ask-matt` to pick a flow. The
per-repo configuration those skills read lives in `docs/agents/`.

### Issue tracker

Linear, workspace *Orc Alchemy*, team key `ORC`, projects **PubDoor** (this
repo) and **Alchemy 5e**, driven through the Linear MCP tools rather than
`gh` or GitHub Issues. See `docs/agents/issue-tracker.md`.

### Triage labels

The five canonical labels (`needs-triage`, `needs-info`, `ready-for-agent`,
`ready-for-human`, `wontfix`), mapped one-to-one onto workspace labels that
already exist in Linear. See `docs/agents/triage-labels.md`.

### Domain docs

Single-context: one `CONTEXT.md` plus `docs/adr/` at the repo root, both
created lazily by `/domain-modeling`. See `docs/agents/domain.md`.
