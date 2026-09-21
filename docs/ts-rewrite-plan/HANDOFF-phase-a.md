# Phase A Handoff — start here

You are picking up **Phase A** of the active plan (`docs/ts-rewrite-plan/`,
Plan Set 2): the compiled engine package `@dmv/pubdoor`, built **in this
fork** so a new TypeScript app can consume the existing Clojure(Script)
rules engine as a library. All 24 Phase A decisions were reviewed and
approved on 2026-09-20. **M0 (fixtures) is done**; the next milestone is
M1. The work is tracked in Linear (project **PubDoor**); this document is
the on-ramp — environment, repository state, the M1 sequence, the rules —
and points into the plan for detail rather than repeating it.

## 1. Read these first (30 minutes)

In order:

1. `CLAUDE.md` — the architecture facts you must not contradict.
2. `docs/ts-rewrite-plan/00-repo-strategy.md` — why Phase A is here and
   Phase B is elsewhere; the directory layout you will create.
3. `docs/ts-rewrite-plan/02-engine-library.md` — build scope, the facade
   surface, the eight engine wrinkles, the four permitted patches.
4. `docs/ts-rewrite-plan/01-compatibility-contract.md` — the character
   quirks (R1–R10) and the content-identity rule.
5. `docs/ts-frontend-plan/03-engine-package.md` — the base facade design
   (Plan Set 1); doc 02 above is the delta on top of it.
6. `docs/ts-frontend-plan/01-reference-app.md` — how to run the old app
   and capture fixtures.
7. `fixtures/README.md` — what M0 produced, the exact conversion rules
   `evaluate().built` must reproduce, and nine *Findings* that correct the
   plan (R4, drift form 10, the Dueling condition, JS-only semantics).

Skim later: `06-milestones-and-risks.md` (M0/M1 rows), `03-character-import-and-storage.md`
(what `importCharacter` must do), `04-homebrew.md` (what `parseOrcbrew`
must expose).

## 2. State of the repository when you arrive

- `develop` carries the plan docs, `CLAUDE.md`, and **M0 complete**: the
  fixture set under `fixtures/` (11 golden characters, 3 real + 8 synthetic
  legacy entities, 15 `.orcbrew` packs with template dumps, the SRD
  baseline) and the oracle scripts under `scripts/`, merged from the
  `engine` branch as PR #4. Read `fixtures/README.md` §Findings before
  writing facade code.
- Nothing of M1 exists yet: no `engine-js/`. You create it. Work on the
  **`engine`** branch, recreated from `develop` (the earlier `engine`
  branch is merged).
- The engine you are packaging is unmodified upstream code. The only
  intended source changes are the four patches in doc 02 §Patches.
- Decisions already made (do not re-open without the user): package name
  `@dmv/pubdoor`; shadow-cljs `:esm` + `:advanced`; build scope and
  exclusions; plain-JS boundary with one-pass extraction; hand-written
  `.d.ts`; fixtures generated here and snapshot-copied to the app repo;
  CI on the `engine` branch; manual tagged publish.
- Linear, project **PubDoor**: M0 follow-ups are ORC-11 to ORC-14; M1 is
  ORC-15 to ORC-26; the engine half of M3 is ORC-27 to ORC-43. Each issue
  carries its acceptance criteria — move it to *In Progress* when you start
  and *Done* when its criteria hold.

## 3. Environment

The devcontainer is the supported path (`docs/GETTING-STARTED.md`). Once in
it:

```sh
./scripts/dev-setup.sh      # deps, Datomic init, test user
./menu start server         # backend on :8890 (with nREPL)
./menu start figwheel       # ClojureScript hot-reload on :3449
```

Then open `http://localhost:8890` and log in as **test@test.com /
testpass** (created by dev-setup; `./menu add <user> <pass>` adds more).
Docker alternative: `./run --auto && docker compose up --build -d`
(`docs/DOCKER.md`).

Verification commands you will use constantly:

```sh
lein test                   # test/clj, test/cljc, test/cljs
lein lint                   # clj-kondo, errors only
lein repl                   # for the dump scripts
```

Library versions the engine namespaces depend on (from `project.clj`), which
your `shadow-cljs.edn` must declare: `re-frame 1.4.4`, `reagent 2.0.1`,
`cljs-http 0.1.49`, `bidi 2.1.6`, `com.cognitect/transit-cljs 0.8.280`.
Reagent also needs `react`, `react-dom` and `create-react-class` installed
from npm even though the facade never renders anything.

shadow-cljs itself installs from npm (`shadow-cljs-jar` bundles the JVM
side), so M1 does not depend on Clojars if the ClojureScript libraries are
supplied as source paths (`fixtures/README.md` finding 8; the M0 run had no
Clojars access and used `scripts/oracle-env.sh` instead of Leiningen).

## 4. M0 — fixtures (done)

M0 is complete and merged (PR #4). `fixtures/README.md` is the reference:
the layout, the exact JSON conversion rules `evaluate().built` must
reproduce (`orcpub.oracle/->plain`), the function-valued attribute keys
(`armor-class-with-armor`, `weapon-modifiers`, …), the template shape and
delta algorithm M3 must reproduce, and the regeneration commands
(`scripts/golden-characters.clj`, `scripts/dump-template.clj`,
`scripts/dump-built-character.clj`). Generation is deterministic — a
regeneration on unchanged engine source must produce no diff.

Left for later, as Linear issues on the M0 milestone: the UI spot check
against a character saved in the old UI (ORC-11), a third openly-licensed
community pack (ORC-12), a `lein test` run with Clojars access (ORC-13),
and folding the findings back into the plan docs (ORC-14).

## 5. M1 — the engine package (target: ~2–3 weeks)

Goal: `@dmv/pubdoor@0.1.x` published, with `evaluate`, the mutation
functions and `importCharacter`, and the golden tests green.

### 5.1 Scaffold `engine-js/`

```
engine-js/
  shadow-cljs.edn
  package.json           ; "name": "@dmv/pubdoor", "type": "module", files: dist/, types/
  src/orcpub/facade.cljs
  types/index.d.ts
  test/*.test.ts         ; vitest, reads ../fixtures
```

Starter `shadow-cljs.edn` (adjust, don't trust blindly):

```clojure
{:source-paths ["src" "../src/cljc" "../src/cljs"]
 :dependencies [[re-frame "1.4.4"] [reagent "2.0.1"] [cljs-http "0.1.49"]
                [bidi "2.1.6"] [com.cognitect/transit-cljs "0.8.280"]]
 :builds {:pubdoor {:target :esm
                    :output-dir "dist"
                    :modules {:pubdoor {:exports {evaluate orcpub.facade/evaluate
                                                  ;; one entry per ^:export fn
                                                  }}}
                    :compiler-options {:optimizations :advanced}}}}
```

`:source-paths` pointing at `../src/cljs` will make shadow-cljs *see* the
whole old UI; only what the facade requires is compiled in. Do not require
`orcpub.core`, `orcpub.dnd.e5.views`, or `orcpub.dnd.e5.events` from the
facade — the build scope is doc 02 §Where the engine is built, and
`pdf_spec`, `character/random`, `char_decision_tree` and `templates/*` must
stay unreferenced.

First milestone inside M1: a single exported `hello` function compiles
under `:advanced`, imports from a `.ts` file, and returns a plain JS value.
Timebox this; it is where shadow-cljs friction lives. Then remove `hello`.

### 5.2 Facade, in this order

Each step is a Linear issue with its acceptance criteria; the order is the
recommended sequence, and steps 4–8 can interleave once step 3 is green.

| Step | Linear | What |
|---|---|---|
| 1 | ORC-15 | Scaffold `engine-js/`; a `hello` export compiles under `:advanced` and imports from TS (timebox it; then remove `hello`) |
| 2 | ORC-16 | `evaluate(strict, homebrew?)` → `{ built, selections }`: build via `char5e/from-strict` → `entity/build` with `t5e/template` (see `subs.cljs:300-347`); one-pass extraction with the `fixtures/README.md` conversion rules; `selections` flattened with `actualPath`; memoized on the input |
| 3 | ORC-17 | Golden tests: every SRD golden character and legacy fixture matches its `expected.json` and `selections.json`; plus the ordering test |
| 4 | ORC-18 | Port `warlock_test.clj` and the three `character_test.clj` round-trips |
| 5 | ORC-19 | Mutations (`select`, `deselect`, `setValue`, `setField`, `addLevel`, `removeLevel`, `setClass`, `addStartingEquipment`) and the `event_handlers_test` port |
| 6 | ORC-20, ORC-21 | Patch D1 (re-enable the legacy key migration, `character.cljc:130-165`), then `importCharacter` / `exportCharacter` with the R5 `xps` fix and the `fixtures/legacy/` suite |
| 7 | ORC-22 | Patch D2: `app-db` reads in `options.cljc` → the entity; the Dueling arity and off-hand condition; `fighter-5` is the test |
| 8 | ORC-23 | `autofill(entity)` — the fixed-point loop from `events.cljs:310` |
| 9 | ORC-24, ORC-25, ORC-26 | `types/index.d.ts` by hand; CI on `engine`; publish `0.1.0` (tag `pubdoor-v0.1.0`; record the registry choice on ORC-26) |

`buildTemplate`, `parseOrcbrew`, `orcbrewToEdn`, `reconcileMissingContent`,
the content lists and the `keys.*` helpers are **M3** (ORC-27 onward), not
M1 — leave stubs out entirely rather than shipping half of them.

### 5.3 Things that will bite you (from doc 02 §Wrinkles)

- Built-character attributes are lazy closures; never hand one to JS.
  Extract once.
- Selection order matters. Keep selections as arrays in JSON; never round-
  trip through a key-ordered object.
- `available-selections` needs the *built* character — build first.
- `ref` selections store data at a global path (`entity/actual-path`),
  not their tree position.
- `options.cljc` requires re-frame; the namespace must load even though
  nothing subscribes. `re-frame.db/app-db` reads inside modifiers are patch
  D2.
- Multi-select option paths carry no index (`entity.cljc:299`).
- `:advanced` renames everything not `^:export`ed or listed in `:exports`.

### 5.4 M1 exit criteria

The M1 milestone in Linear closes when ORC-15 to ORC-26 are done, which
amounts to:

- [ ] `npm install @dmv/pubdoor` works from the registry; `import { evaluate } from "@dmv/pubdoor"` type-checks
- [ ] Every M0 golden character: `evaluate().built` equals `expected.json`
- [ ] Ported warlock, character round-trip, and event-handler tests green
- [ ] Legacy fixtures R1–R9 import; R5/R7 covered explicitly
- [ ] Patches D1 and D2 committed with tests; D3/D4 deferred to M3 with `parseOrcbrew`
- [ ] CI green on `engine`

## 6. Rules

- **Do not** change content keys, `common/name-to-kw`, the 16 explicit
  spell keys, subclass selection keys, or `ref` paths (contract C3).
- **Do not** enable any `#_`-disabled content or reference `templates/`.
- **Do not** include `pdf_spec.cljc` or `character/random.cljc` in the
  build. No PDF feature; no non-SRD name tables.
- Engine source changes are limited to patches D1–D4. Anything else you
  think the engine needs is a facade concern — write it in `facade.cljs`
  and note it in the PR.
- Keep `lein test` green throughout; the old app must keep working.
- Commit small; open a PR from `engine` into `develop` at the end of M1
  (M0's is merged). Name the Linear issue (`ORC-nn`) in each commit or PR.
- If a plan doc cites a line number that has moved, regenerate the
  citation from the source; don't guess.

## 7. When you're done

Post the M1 exit report as a comment on ORC-26: the published package
version, the fixture counts, the golden-test pass count, and anything in
the plan that turned out to be wrong (with the file and line). Phase B
starts by creating the app repository (Alchemy 5e, ORC-44) and copying
`fixtures/` and `docs/ts-rewrite-plan/` into it — see doc 00 §Phase B.
