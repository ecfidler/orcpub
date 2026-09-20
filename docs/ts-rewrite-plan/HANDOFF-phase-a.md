# Phase A Handoff — start here

You are picking up **Phase A** of the active plan (`docs/ts-rewrite-plan/`,
Plan Set 2): produce the fixture set and the compiled engine package
`@dmv/pubdoor` **in this fork**, so a new TypeScript app can later consume
the existing Clojure(Script) rules engine as a library. All 24 Phase A
decisions were reviewed and approved on 2026-09-20; this document turns
them into a starting sequence. It is self-contained, but it points into the
plan for detail rather than repeating it.

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

Skim later: `06-milestones-and-risks.md` (M0/M1 rows), `03-character-import-and-storage.md`
(what `importCharacter` must do), `04-homebrew.md` (what `parseOrcbrew`
must expose).

## 2. State of the repository when you arrive

- Branch `planning` carries the plan docs and `CLAUDE.md` (PR #2 into
  `develop`; it may or may not be merged yet — check). Start Phase A on a
  branch named **`engine`** off `planning` (or off `develop` if PR #2 has
  merged).
- Nothing of Phase A exists yet: no `engine-js/`, no `fixtures/`, no
  `scripts/*.clj`. You create them.
- The engine you are packaging is unmodified upstream code. The only
  intended source changes are the four patches in doc 02 §Patches.
- Decisions already made (do not re-open without the user): package name
  `@dmv/pubdoor`; shadow-cljs `:esm` + `:advanced`; build scope and
  exclusions; plain-JS boundary with one-pass extraction; hand-written
  `.d.ts`; fixtures generated here and snapshot-copied to the app repo;
  CI on the `engine` branch; manual tagged publish.

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

## 4. M0 — fixtures (target: ~1 week)

Goal: a committed `fixtures/` directory with real inputs and
oracle-produced expected outputs. Everything in M1 is tested against it.

### 4.1 Layout

```
fixtures/
  README.md                    ; what each set is, how it was produced, fork commit
  characters/
    <name>.strict.json         ; strict entity (Transit-decoded, string keys)
    <name>.expected.json       ; built values: every accessor in §4.3
  orcbrew/
    <pack>.orcbrew             ; real packs + one synthetic file per drift form
    <pack>.template.json       ; buildTemplate expected output (M3 uses it)
  legacy/
    <quirk>.strict.json        ; one per R1–R9 (doc 01 §C2)
scripts/
  dump-built-character.clj     ; strict entity → expected.json
  dump-template.clj            ; plugins map → template.json
```

### 4.2 Golden characters — build them in code, not by clicking

The full target is 12 classes × levels 1/5/11/20 plus multiclass, homebrew
and legacy cases. Do **not** build 50 characters in the UI. The existing
end-to-end test shows the shortcut: `test/cljc/orcpub/dnd/e5/warlock_test.clj:106-232`
constructs a raw entity as a Clojure map and builds it against the real
template. Write golden characters the same way — a raw entity per case,
then `char5e/to-strict` to produce the `.strict.json` — and reserve the UI
for a handful of spot checks that the code-built entities match what the
app produces (save one from the UI, fetch it via
`GET /dnd/5e/characters/<id>` with `Authorization: Token <jwt>`, compare).

Order of work: Fighter and Wizard at all four levels first (they exercise
equipment, fighting styles, and prepared casting), then one multiclass
(Fighter/Wizard is fine), then Warlock (reuse the warlock test), then the
rest. M1 can start once Fighter and Wizard exist; keep adding classes in
parallel.

Ready-made legacy fixtures: the three real Datomic entities in
`test/cljc/orcpub/dnd/e5/character_test.clj:100-113` — copy them as-is.

### 4.3 The dump script

`scripts/dump-built-character.clj`, run in `lein repl`: read a strict
entity, `char5e/from-strict`, `entity/build` against `t5e/template`, then
evaluate the accessor list and write JSON. The accessor list is
`character.cljc:363-738` (one thin `defn` per attribute) — mirror the
`character-subs` map at `src/cljs/orcpub/dnd/e5/subs.cljs:628-736`, which is
the same list keyed for the UI. Convert namespaced keywords to
`"ns/name"` strings and sets to sorted arrays so the output is stable.
Functions-valued attributes (`armor-class-with-armor`, `spell-save-dc`,
`weapon-attack-modifier`, …) can't be serialized directly: evaluate them
against fixed arguments (each equipped armor/shield combination; each
spellcasting ability; each carried weapon with finesse on/off) and record
the results — doc 02 §Facade surface lists what the facade exposes, and
that is what the expected output must cover.

JSON writing: check `project.clj` for `cheshire` (a transitive dependency
of Pedestal) or add `org.clojure/data.json` to the `:dev` profile.

### 4.4 Homebrew fixtures

- Real: `test/duplicate-external-a.orcbrew`, `test/duplicate-external-b.orcbrew`,
  plus community packs. Search GitHub for `.orcbrew` files; record each
  pack's source and license in `fixtures/README.md` and prefer packs whose
  authors publish them openly.
- Synthetic: one file per drift form in doc 01 §C1 (ten of them), each a
  minimal pack that exercises exactly that form.
- Expected output: `scripts/dump-template.clj` runs the old subscription
  chain's functions over a plugins map and writes the resulting template
  shape (selection keys, option keys, min/max) — see doc 02
  §De-re-framing for which functions. This is what M3 compares against;
  producing it in M0 while the REPL is warm saves a round trip.

### 4.5 M0 exit criteria

- [ ] Fighter and Wizard at four levels, one multiclass, the warlock case,
      the three legacy entities: each with `.strict.json` + `.expected.json`
- [ ] Both real `.orcbrew` fixtures + ≥3 community packs + 10 drift files,
      each with a `.template.json`
- [ ] `fixtures/README.md` records the producing fork commit and how to
      regenerate

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

1. `evaluate(strictEntityJson, homebrew?)` → `{ built, selections }`.
   Build: `char5e/from-strict` → `entity/build` with `t5e/template` (see
   how `subs.cljs:300-347` assembles the template and calls `build`).
   Extract `built` in one pass to a plain object — the same accessor list
   as the dump script, so `expected.json` and `evaluate().built` are
   directly comparable. `selections` from `entity/available-selections`,
   flattened with each selection's `actualPath` (doc 02 wrinkle 5).
   Memoize on the input JSON string.
2. **First golden test**: `evaluate(fighter-1.strict.json).built` deep-equals
   `fighter-1.expected.json`. Then all of M0's characters.
3. Port `warlock_test.clj` assertions and the three `character_test.clj`
   round-trips as vitest tests (`exportCharacter(importCharacter(x))` ≡ x).
4. Mutations: `select`, `deselect`, `setValue`, `setField`, `addLevel`,
   `removeLevel`, `setClass`, `addStartingEquipment` — backed by
   `event_handlers.cljc` and `character.cljc:752-856`; port the round-trip
   tests in `test/cljc/orcpub/dnd/e5/event_handlers_test.clj`.
5. `importCharacter(transitText | json)` and `exportCharacter(entity)`:
   inherited `from-strict`/`to-strict` **plus** the two non-inherited
   quirks — string `xps` (R5) and the legacy unnamespaced-key migration
   (R7, patch D1: re-enable `character.cljc:130-165`). One fixture per
   quirk from `fixtures/legacy/`.
6. `autofill(entity)` — the fixed-point loop from `events.cljs:310`.
7. Patch D2: audit `grep -rn "re-frame\|app-db\|subscribe\|dispatch" src/cljc`
   and make each read take its input from the entity. Add a golden
   character with the Dueling fighting style so the patch is tested.
8. `types/index.d.ts` written by hand alongside each function.
9. Publish `0.1.0` (tag `pubdoor-v0.1.0`); CI on `engine` runs the build
   and vitest on every push.

`buildTemplate`, `parseOrcbrew`, `orcbrewToEdn`, `reconcileMissingContent`,
the content lists and the `keys.*` helpers are **M3**, not M1 — leave stubs
out entirely rather than shipping half of them.

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
- Commit small; open a PR from `engine` into `planning` (or `develop` if
  the plans have merged) at the end of M0 and again at the end of M1.
- If a plan doc cites a line number that has moved, regenerate the
  citation from the source; don't guess.

## 7. When you're done

Report: the published package version, the fixture counts, the golden-test
pass count, and anything in the plan that turned out to be wrong (with the
file and line). Phase B starts by creating the app repository and copying
`fixtures/` and `docs/ts-rewrite-plan/` into it — see doc 00 §Phase B.
