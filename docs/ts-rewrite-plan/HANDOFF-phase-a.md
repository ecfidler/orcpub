# Phase A handoff: start here

You are picking up Phase A of the active plan (`docs/ts-rewrite-plan/`,
Plan Set 2): the compiled engine package `@dmv/pubdoor`, built in this fork
so that a new TypeScript app can use the existing rules engine as a
library. All 24 Phase A decisions were reviewed and approved on 2026-09-20.
M0 (fixtures) is done. The next milestone is M1. Linear project PubDoor
tracks the work. This document is the starting point: the environment, the
repository state, the M1 sequence, and the rules. It points into the plan
for detail rather than repeating it.

## 1. Read these first (30 minutes)

In order:

1. `CLAUDE.md`: the architecture facts you must not contradict.
2. `docs/ts-rewrite-plan/00-repo-strategy.md`: why Phase A is here and
   Phase B is elsewhere, and the directory layout you will create.
3. `docs/ts-rewrite-plan/02-engine-library.md`: the build scope, the facade
   API, the eight engine wrinkles, and the four permitted patches.
4. `docs/ts-rewrite-plan/01-compatibility-contract.md`: the character
   quirks (R1 to R10) and the content-identity rule.
5. `docs/ts-frontend-plan/03-engine-package.md`: the base facade design
   (Plan Set 1). Doc 02 above is the delta on top of it.
6. `docs/ts-frontend-plan/01-reference-app.md`: how to run the old app and
   capture fixtures.
7. `fixtures/README.md`: what M0 produced, the exact conversion rules
   `evaluate().built` must reproduce, and nine findings that correct the
   plan (R4, drift form 10, the Dueling condition, and JS-only semantics).

Skim later: `06-milestones-and-risks.md` (the M0 and M1 rows),
`03-character-import-and-storage.md` (what `importCharacter` must do),
`04-homebrew.md` (what `parseOrcbrew` must expose), and
`docs/reports/2024-rules-support.md` §Decision (why `evaluate` takes a
`rules` option).

## 2. State of the repository when you arrive

- `develop` carries the plan docs, `CLAUDE.md`, and the completed M0: the
  fixture set under `fixtures/` (11 golden characters, 3 real and 8
  synthetic legacy entities, 15 `.orcbrew` packs with template dumps, and
  the SRD baseline) and the oracle scripts under `scripts/`, merged from
  the `engine` branch as PR #4. Read `fixtures/README.md` §Findings before
  writing facade code.
- Nothing of M1 exists yet. There is no `engine-js/`. You create it. Work
  on the `engine` branch, recreated from `develop`. The earlier `engine`
  branch is merged.
- The engine you are packaging is unmodified upstream code. The only
  intended source changes are the four patches in doc 02 §Patches.
- These decisions are already made. Do not reopen them without the user:
  the package name `@dmv/pubdoor`, shadow-cljs with `:esm` and `:advanced`,
  the build scope and exclusions, a plain-JS boundary with one-pass
  extraction, a hand-written `.d.ts`, fixtures generated here and
  snapshot-copied to the app repo, CI on the `engine` branch, and a manual
  tagged publish. Also decided, on 2026-09-23 (ORC-94): `evaluate` takes a
  `rules` option that defaults to `"2014"` and rejects any other value, and
  the `.d.ts` splits the built character into an edition-neutral part and a
  `Built2014` extension. 2024 rules are out of scope for Phase A.
- In Linear project PubDoor, the M0 follow-ups are ORC-11 to ORC-14, M1 is
  ORC-15 to ORC-26, and the engine half of M3 is ORC-27 to ORC-43. Each
  issue carries its acceptance criteria. Move an issue to In Progress when
  you start it and to Done when its criteria hold.

## 3. Environment

The devcontainer is the supported setup (`docs/GETTING-STARTED.md`). Once
inside it, run:

```sh
./scripts/dev-setup.sh      # deps, Datomic init, test user
./menu start server         # backend on :8890 (with nREPL)
./menu start figwheel       # ClojureScript hot-reload on :3449
```

Then open `http://localhost:8890` and log in as `test@test.com` with the
password `testpass`. The dev-setup script creates that user, and
`./menu add <user> <pass>` adds more. The Docker alternative is
`./run --auto && docker compose up --build -d` (`docs/DOCKER.md`).

You will run these verification commands constantly:

```sh
lein test                   # test/clj, test/cljc, test/cljs
lein lint                   # clj-kondo, errors only
lein repl                   # for the dump scripts
```

The engine namespaces depend on these library versions from `project.clj`,
and your `shadow-cljs.edn` must declare them: `re-frame 1.4.4`,
`reagent 2.0.1`, `cljs-http 0.1.49`, `bidi 2.1.6`, and
`com.cognitect/transit-cljs 0.8.280`. Reagent also needs `react`,
`react-dom`, and `create-react-class` installed from npm, even though the
facade never renders anything.

shadow-cljs itself installs from npm, because `shadow-cljs-jar` bundles the
JVM side. M1 therefore does not depend on Clojars if the ClojureScript
libraries are supplied as source paths (`fixtures/README.md` finding 8).
The M0 run had no Clojars access and used `scripts/oracle-env.sh` instead
of Leiningen.

## 4. M0: fixtures (done)

M0 is complete and merged (PR #4). `fixtures/README.md` is the reference.
It covers the layout, the exact JSON conversion rules `evaluate().built`
must reproduce (`orcpub.oracle/->plain`), the function-valued attribute
keys (`armor-class-with-armor`, `weapon-modifiers`, and others), the
template shape and delta algorithm M3 must reproduce, and the regeneration
commands (`scripts/golden-characters.clj`, `scripts/dump-template.clj`,
and `scripts/dump-built-character.clj`). Generation is deterministic.
Regenerating on unchanged engine source must produce no diff.

Four items are left for later as Linear issues on the M0 milestone: the UI
spot check against a character saved in the old UI (ORC-11), a third
openly licensed community pack (ORC-12), a `lein test` run with Clojars
access (ORC-13), and folding the findings back into the plan docs (ORC-14).

## 5. M1: the engine package (target about 2 to 3 weeks)

The goal is `@dmv/pubdoor@0.1.x` published, with `evaluate`, the mutation
functions, and `importCharacter`, and the golden tests green.

### 5.1 Scaffold `engine-js/`

```
engine-js/
  shadow-cljs.edn
  package.json           ; "name": "@dmv/pubdoor", "type": "module", files: dist/, types/
  src/orcpub/facade.cljs
  types/index.d.ts
  test/*.test.ts         ; vitest, reads ../fixtures
```

A starter `shadow-cljs.edn`. Adjust it rather than trusting it:

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

With `:source-paths` pointing at `../src/cljs`, shadow-cljs can see the
whole old UI, but it compiles in only what the facade requires. Do not
require `orcpub.core`, `orcpub.dnd.e5.views`, or `orcpub.dnd.e5.events`
from the facade. The build scope is doc 02 §Where the engine is built, and
`pdf_spec`, `character/random`, `char_decision_tree`, and `templates/*`
must stay unreferenced.

The first milestone inside M1 is a single exported `hello` function that
compiles under `:advanced`, imports from a `.ts` file, and returns a plain
JS value. Timebox this step, because it is where the shadow-cljs setup
problems appear. Then remove `hello`.

### 5.2 Facade, in this order

Each step is a Linear issue with its acceptance criteria. The order is the
recommended sequence, and steps 4 to 8 can interleave once step 3 is
green.

| Step | Linear | What |
|---|---|---|
| 1 | ORC-15 | Scaffold `engine-js/`. A `hello` export compiles under `:advanced` and imports from TypeScript. Timebox it, then remove `hello` |
| 2 | ORC-16 | `evaluate(strict, { rules?, homebrew? })`, returning `{ built, selections }`. `rules` defaults to `"2014"`, and any other value throws. Build with `char5e/from-strict` and then `entity/build` with `t5e/template` (see `subs.cljs:300-347`). One-pass extraction with the `fixtures/README.md` conversion rules. `selections` flattened with `actualPath`. Memoized on the input |
| 3 | ORC-17 | Golden tests: every SRD golden character and legacy fixture matches its `expected.json` and `selections.json`, plus the ordering test |
| 4 | ORC-18 | Port `warlock_test.clj` and the three `character_test.clj` round-trips |
| 5 | ORC-19 | Mutations (`select`, `deselect`, `setValue`, `setField`, `addLevel`, `removeLevel`, `setClass`, `addStartingEquipment`) and the `event_handlers_test` port |
| 6 | ORC-20, ORC-21 | Patch D1 (re-enable the legacy key migration, `character.cljc:121-178`), then `importCharacter` and `exportCharacter` with the R5 `xps` fix and the `fixtures/legacy/` suite |
| 7 | ORC-22 | Patch D2: the `app-db` reads in `options.cljc` read from the entity instead, plus the Dueling arity and off-hand condition. `fighter-5` is the test |
| 8 | ORC-23 | `autofill(entity)`, the fixed-point loop from `events.cljs:310` |
| 9 | ORC-24, ORC-25, ORC-26 | `types/index.d.ts` by hand, with `Rules` and the `Built2014` split, CI on `engine`, and publishing `0.1.0` with the tag `pubdoor-v0.1.0`. Record the registry choice on ORC-26 |

`buildTemplate`, `parseOrcbrew`, `orcbrewToEdn`, `reconcileMissingContent`,
the content lists, and the `keys.*` helpers are M3 (ORC-27 onward), not
M1. Leave them out entirely rather than shipping stubs.

### 5.3 Known problems (from doc 02 §Wrinkles)

- Built-character attributes are lazy closures. Never hand one to JS.
  Extract once.
- Selection order matters. Keep selections as arrays in JSON, and never
  round-trip them through a key-ordered object.
- `available-selections` needs the built character. Build first.
- `ref` selections store data at a global path (`entity/actual-path`),
  not their tree position.
- `options.cljc` requires re-frame, so the namespace must load even though
  nothing subscribes. The `re-frame.db/app-db` reads inside modifiers are
  patch D2.
- Multi-select option paths carry no index (`entity.cljc:299`).
- `:advanced` renames everything that is not marked `^:export` or listed
  in `:exports`.

### 5.4 M1 exit criteria

The M1 milestone in Linear closes when ORC-15 to ORC-26 are done, which
amounts to:

- [ ] `npm install @dmv/pubdoor` works from the registry, and `import { evaluate } from "@dmv/pubdoor"` type-checks
- [ ] For every M0 golden character, `evaluate().built` equals `expected.json`
- [ ] The ported warlock, character round-trip, and event-handler tests are green
- [ ] The legacy fixtures for R1 to R9 import, with R5 and R7 covered explicitly
- [ ] Patches D1 and D2 are committed with tests. D3 and D4 are deferred to M3 with `parseOrcbrew`
- [ ] CI is green on `engine`

## 6. Rules

- Do not change content keys, `common/name-to-kw`, the 16 explicit spell
  keys, subclass selection keys, or `ref` paths (contract C3).
- Do not enable any content disabled with `#_`, and do not reference
  `templates/`.
- Do not include `pdf_spec.cljc` or `character/random.cljc` in the build.
  There is no PDF feature and there are no non-SRD name tables.
- Engine source changes are limited to patches D1 to D4. Anything else you
  think the engine needs is a facade concern. Write it in `facade.cljs` and
  note it in the PR.
- Keep `lein test` green throughout. The old app must keep working.
- Commit small. Open a PR from `engine` into `develop` at the end of M1.
  M0's PR is merged. Name the Linear issue (`ORC-nn`) in each commit or PR.
- If a plan doc cites a line number that has moved, regenerate the citation
  from the source. Do not guess.

## 7. When you are done

Post the M1 exit report as a comment on ORC-26: the published package
version, the fixture counts, the golden-test pass count, and anything in
the plan that turned out to be wrong, with the file and line. Phase B
starts by creating the app repository (Alchemy 5e, ORC-44) and copying
`fixtures/` and `docs/ts-rewrite-plan/` into it. See doc 00 §Phase B.
