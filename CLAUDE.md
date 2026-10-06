# CLAUDE.md

Guidance for AI agents working in this repository.

## What this repository is

Dungeon Master's Vault, a fork of OrcPub2, is a Clojure and ClojureScript
D&D 5e character builder. The backend is Pedestal and Datomic in
`src/clj`. The frontend is Reagent and re-frame in `src/cljs`. The rules
engine and all game content are shared `.cljc` code in `src/cljc`. See
`README.md` and `docs/STACK.md`.

The most important architectural fact is that the rules engine runs in
the browser. The backend stores only a character's raw choice tree, the
"strict entity" defined in `src/cljc/orcpub/entity/strict.cljc`.
Everything computed, such as AC, spell slots, and available options, comes
from `entity.cljc`, `template.cljc`, `modifiers.cljc`, and
`dnd/e5/template_base.cljc`, all under `src/cljc/orcpub/`.

## Read the planning documents before any frontend or rewrite work

Two plan sets in orc-alchemy describe the intended direction for a
TypeScript frontend. They come from an investigation of this codebase and
cite file and line numbers. Prefer them to re-deriving the architecture.

Issue tracking is in Linear, workspace *Orc Alchemy*
(https://linear.app/orc-alchemy):

- PubDoor tracks Phase A, the engine package built in this fork:
  https://linear.app/orc-alchemy/project/pubdoor-13db9e66264b
- Alchemy 5e tracks Phase B, the new app in its own repository:
  https://linear.app/orc-alchemy/project/alchemy-5e-9db66f3ef51e
- The plan overview is a team document that links the compatibility
  contract and the risk register:
  https://linear.app/orc-alchemy/document/plan-overview-typescript-rewrite-on-the-compiled-engine-1afe72ecb90d

Milestones, issue status, and sequencing are in Linear. The documents
below are the technical reference. When the two disagree about what is
built, the docs win. When they disagree about status, Linear wins.
Reference the issue identifier (`ORC-nn`) in commits and PRs.

The plan documents live in
[`ecfidler/orc-alchemy`](https://github.com/ecfidler/orc-alchemy), the
TypeScript monorepo, under `docs/plan/`. They moved out of this fork on
2026-09-29 (ORC-101). The last fork commit that has them is `26f57e07`,
and their file and line citations refer to this fork at that commit.
Plan Set 2, `docs/plan/`, is the active plan. When the user says "the
plan" without qualification, they mean Plan Set 2. Plan Set 1,
`docs/plan/plan-set-1/`, is reference material that Plan Set 2 builds on.

- If you are working on Phase A, the engine package in this fork, read
  `docs/plan/HANDOFF-phase-a.md` in orc-alchemy first. The key documents
  are `01-compatibility-contract.md`, which lists what must import and
  export and the legacy data quirks, and `02-engine-library.md`, which
  covers the facade API, the engine wrinkles, and the few upstream patches
  needed. M0, the fixtures milestone, is done: `fixtures/` and `scripts/`
  hold the oracle outputs and tooling, described in `fixtures/README.md`,
  whose Findings section corrects the plan in a few places.
- orc-alchemy `docs/reports/2024-rules-support.md` §Decision records how
  2024 rules (SRD 5.2) arrive: option E. `@pubdoor/dmv` serves 2014 only,
  and a later TypeScript engine, tracked in Linear project *2024 engine*,
  adds 2024. Read it before any work on rules editions, the `rules` tag,
  or 2024 content.

## Facts the plans depend on

Do not contradict these without checking the source.

- Shipped game content is SRD 5.1 only. Non-SRD content exists in the
  source, either behind the `#_` reader macro, which discards it, or
  unreferenced under `templates/`. Do not enable it.
- Built-in races, backgrounds, and languages are defined in
  `src/cljs/orcpub/dnd/e5/spell_subs.cljs`, not in `src/cljc`.
- Saved characters and `.orcbrew` files reference content by keyword key.
  `common/name-to-kw` derives the key from the name, and 16 spells carry
  explicit keys. Changing a key breaks saved data.
- `.orcbrew` files are EDN. The import pipeline is
  `src/cljs/orcpub/dnd/e5/import_validation.cljs`.
  `docs/ORCBREW_FILE_VALIDATION.md` and orc-alchemy
  `docs/plan/01-compatibility-contract.md` document the format and its
  known drift.
- Selection order in a strict entity carries meaning. `entity.cljc` uses
  `array-map` deliberately to preserve it.

## Working in this repo

- `docs/GETTING-STARTED.md` covers dev setup, `docs/DOCKER.md` covers
  Docker, and `docs/ENVIRONMENT.md` covers environment variables.
- Run the tests with `lein test`. Engine golden-test candidates are
  `test/cljc/orcpub/dnd/e5/warlock_test.clj` and the round-trip entities
  in `character_test.clj`.
- `views.cljs` (339 KB) and `events.cljs` (156 KB) are large. Search them
  for the keyword you need rather than reading top to bottom.
- Do not edit the plan documents' cited line numbers by hand. If the code
  moves, regenerate them from the source.

## Documentation

- Do not edit the upstream Dungeon Master's Vault documents, for example
  `README.md`, `CHANGELOG.md`, and `docs/*.md`.
  `docs/pubdoor/doc-ownership.md` lists which documents are upstream and
  which the fork owns.
- Put new project documents in orc-alchemy `docs/`. Put a document here
  only when it describes only the fork. Then put it in `docs/pubdoor/`,
  an Open Knowledge Format (OKF) v0.2 bundle.
- orc-alchemy `docs/conventions/okf-profile.md` gives the OKF rules. To
  check this bundle from an orc-alchemy checkout next to this one, run
  `bun scripts/okf-check.ts ../orcpub/docs/pubdoor` there.
- orc-alchemy `docs/conventions/writing-standard.md` is the writing
  standard: ASD-STE100 Simplified Technical English at about 80 percent.

## Agent skills

Run `/technical-writing` on docs, PR bodies, Linear issues, and commit
messages.

### Subagents

Use the `opus` model for `Explore` subagents in this repo. Pass
`model: "opus"` on every `Agent` call with `subagent_type: "Explore"`.

### Issue tracker

The issue tracker is Linear: workspace *Orc Alchemy*, team key `ORC`,
projects PubDoor (this repo) and Alchemy 5e. Use the Linear MCP tools, not
`gh` or GitHub Issues. See `docs/agents/issue-tracker.md`.

### Triage labels

The five canonical triage labels (`needs-triage`, `needs-info`,
`ready-for-agent`, `ready-for-human`, `wontfix`) exist in Linear as
workspace labels with the same names. See `docs/agents/triage-labels.md`.

### Domain docs

This repo is single-context: one `CONTEXT.md` and one `docs/adr/` at the
repo root. `/domain-modeling` creates both when first needed. See
`docs/agents/domain.md`.
