# 00 — Repository Strategy

Where the work happens, and how the pieces are split across repositories.
This decision shapes M0–M2 in doc 06 and replaces the "vendor the engine
source" arrangement originally described in doc 02.

## Decision: stage the work at the engine-library seam

The plan has two kinds of work with different toolchains:

- **Clojure-touching work** — capturing fixtures from the running old app,
  building the engine library with shadow-cljs, and the four small engine
  patches. This needs a JVM, Leiningen, the Datomic transactor, and the old
  app itself.
- **TypeScript work** — the new application, its tests, and its backend.
  This needs Node and nothing else.

So the split is:

| Phase | Where | What |
|---|---|---|
| **A** | **This fork** (`ecfidler/orcpub`), on a branch from `planning` | M0 fixtures and the built-value dump script; the engine library at `engine-js/` (shadow-cljs build, facade, `.d.ts`, golden tests); the engine patches as ordinary commits; publishing `@dmv/pubdoor` |
| **B** | **A new repository** (working name `dmv-next`) | Everything from M2 onward: the app, local-first storage, homebrew UI, import tooling, backend. Consumes `@dmv/pubdoor` from a package registry. **No Clojure toolchain, ever.** |

The engine package is the seam because it is a build artifact with a typed
interface: the app repo depends on it exactly as it would on any npm
dependency, and the fork is the only place that can build it.

## What this changes in the other documents

- **Doc 02 "Vendoring"** — superseded. The engine source is not copied into
  the app repo. `engine-js/` lives in this fork beside `src/`, compiles
  against `../src/cljc` and the named `src/cljs` namespaces in place, and
  the four patches are commits to this fork's own source (it is the user's
  fork; there is nothing to track a diff against). The exclusion list
  (`pdf_spec.cljc`, `character/random.cljc`, `char_decision_tree.cljc`,
  `templates/`) becomes shadow-cljs build configuration rather than a copy
  list.
- **Doc 06 milestones** — M0 and M1 happen in this fork; M2 onward in the
  app repo. M1's deliverable is a published package version, not a
  directory.
- **Plan Set 1 doc 03** already describes `engine-js/` in this repo; that
  layout is reused as-is.

## Phase A — this fork

Branch: `engine` (from `planning`). Contents added to the repo:

```
engine-js/
  shadow-cljs.edn          ; :esm target, :advanced; source-paths ["src" "../src/cljc" "../src/cljs"]
  src/orcpub/facade.cljs   ; the exported API (doc 02)
  package.json             ; name @dmv/pubdoor; publishes dist/ + types/
  types/index.d.ts         ; hand-written
  test/                    ; golden tests (vitest) against fixtures/
fixtures/
  characters/*.json        ; captured strict entities (Transit-decoded) + expected built values
  orcbrew/*.orcbrew        ; community packs + one file per drift form
  templates/*.json         ; buildTemplate expected outputs per pack
scripts/
  dump-built-character.clj ; REPL script: strict entity → accessor values (doc 04 of Plan Set 1 §golden tests)
  dump-template.clj        ; plugins → template shape, for the de-re-framing comparison
```

Package name: `@dmv/pubdoor` (chosen at the Phase A decision review, 2026-09-20,
where all 24 Phase A decisions were approved).

Publishing: GitHub Packages under the fork's owner is the least setup
(`npm publish` with a `publishConfig.registry`); public npm is fine too.
Version the package semver-style from `0.1.0`; every engine patch or
facade addition bumps it, and the app repo pins exact versions. CI in this
fork: build the package and run the golden tests on every push to
`engine`.

Why fixtures live here and are *copied* to the app repo: generating them
needs the old app; consuming them doesn't. The app repo's tests must stay
self-contained, so it carries a snapshot under its own `fixtures/` and a
note of which fork commit produced it.

## Phase B — the app repository

Created once `@dmv/pubdoor@0.1.x` exists with `evaluate`, the mutations,
`importCharacter`, and `parseOrcbrew` (i.e. after M1 and the engine half of
M3). Initial contents:

```
dmv-next/
  CLAUDE.md                ; see below
  docs/plan/               ; a copy of docs/ts-rewrite-plan/ and the Plan Set 1 docs it references
  fixtures/                ; snapshot from the fork, with the source commit recorded
  packages/app/            ; Vite + React + TS (Plan Set 1 doc 04 scaffold)
  packages/backend/        ; later (doc 05)
  tools/exporter-bookmarklet/  ; doc 03
```

`CLAUDE.md` in the app repo states: the plan is `docs/plan/` (Plan Set 2 is
active); the engine comes from `@dmv/pubdoor`, built in the `ecfidler/orcpub`
fork under `engine-js/`, which is also the test oracle; the current
milestone; and that the app never imports Clojure or the engine source.
Update the current-milestone line as work progresses — "implement the
plan" is too large a prompt for one session; "we're on M2" is the right
size.

## Working with agents across the two repos

- The repo an agent edits must contain the spec it's implementing. Hence
  the plan copy in `docs/plan/`, not a link to this fork.
- When a golden value is in doubt, regenerate it in the fork (Phase A
  tooling) and copy the fixture across; don't hand-edit expected outputs
  in the app repo.
- A session can attach a second repository when both are genuinely needed
  (e.g. adding a facade function *and* using it). Most sessions need only
  one.
- Once Projects are available, one project spanning both repositories with
  the plan in its instructions fits this split naturally: engine threads
  in the fork, app threads in `dmv-next`.

## Alternatives considered

- **New repo from day one, vendoring the engine source** (the original doc
  02 text): forces a Clojure toolchain into the app repo for the lifetime
  of the project, and re-implements what this fork's build already does.
- **Monorepo inside this fork** (Plan Set 1's `web-ts/` layout): least
  friction to start and a reasonable fallback, but ties the new app's
  history to a Clojure project it will outgrow and makes upstream merges
  from `orcpub/orcpub` noisier. If Phase B ever feels premature, start the
  app under `web-ts/` here and extract it later — the engine seam makes
  that extraction mechanical.
