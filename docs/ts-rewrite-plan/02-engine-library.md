# 02 — The Engine Library

Plan Set 1 doc 03 (`docs/ts-frontend-plan/03-engine-package.md`) describes
the base: a shadow-cljs build of the `.cljc` core behind a small typed facade
(`buildCharacter`, `availableSelections`, `selectOption`, `setValue`,
`randomCharacter`, …). This document covers what a **new app** needs on top
of that, what the engine investigation says a facade author must know, and
how the build is scoped (doc 00 covers where it lives).

## Where the engine is built

**Superseded by doc 00:** the engine is built and published from this fork,
not vendored into the app repo. The build lives at `engine-js/` beside
`src/` (the Plan Set 1 doc 03 layout) and compiles the engine namespaces in
place:

- all of `src/cljc/orcpub/**`;
- from `src/cljs/orcpub/dnd/e5/`: `spell_subs.cljs` (built-in
  races/backgrounds/languages + the homebrew → template pipeline),
  `import_validation.cljs` (`.orcbrew` parse/clean/validate),
  `content_reconciliation.cljs` (missing-content detection),
  `compute.cljs` (pure helpers already extracted from subs);
- `engine-js/src/orcpub/facade.cljs` — the exported API.

Excluded from the build on purpose: `pdf_spec.cljc` (no PDF feature),
`character/random.cljc` (non-SRD name tables — see doc 01),
`char_decision_tree.cljc` (depends on random names; the "newb" builder is
out of scope), everything under `templates/` (unreferenced, non-SRD), all
of `src/clj`.

The four patches below are ordinary commits to this fork's source. The app
repo consumes the published `@dmv/engine` package and never sees Clojure.

## Facade surface (beyond Plan Set 1 doc 03)

| Facade function | Backed by | Notes |
|---|---|---|
| `evaluate(entity, homebrew)` → `{ built, selections }` | `entity/build`, `entity/available-selections`, the template built from `t5e/template` with homebrew merged in | Replaces the old subscription chain; one call, memoized on `(entity, homebrewVersion)` |
| `mutations`: `select`, `deselect`, `setValue`, `setField`, `addLevel`, `removeLevel`, `setClass`, `addStartingEquipment`, … | `event_handlers.cljc`, `character.cljc:752-856` | Pure; each has an old round-trip test to port |
| `importCharacter(transitOrEdn)` → entity | `char5e/from-strict` + the R5/R7 additions from doc 01 | See doc 03 |
| `exportCharacter(entity)` → JSON | `char5e/to-strict` | The new app's own file format (doc 03) |
| `buildTemplate(homebrew)` | the `spell_subs.cljs` chain, lifted to functions | See "De-re-framing" below |
| `parseOrcbrew(text)` → `{ data, log, conflicts }` | `import_validation/validate-import` and friends | Pure; see doc 04 |
| `orcbrewToEdn(plugins)` / `prettyEdn` | `pr-str` / `pprint` | Export |
| `reconcileMissingContent(entity, homebrew)` | `content_reconciliation.cljs` | Suggestions for unresolved keys |
| `content.spells()`, `monsters()`, `magicItems()`, `weapons()`, … | the data namespaces + `magic-items` expansion | Plain JS lists for browse pages; consider a build-time JSON dump instead so those pages code-split away from the engine chunk |
| `keys.selectionKeys()`, `optionKeys()` | walk of the built template | For the C3 identity test |

## De-re-framing `spell_subs.cljs`

The homebrew → template conversion is a chain of `reg-sub`s
(`::e5/plugins` → `plugin-vals` → `plugin-races`/`plugin-classes`/… →
`::races5e/races`/`::classes5e/classes`/… → `::char5e/template-selections`
→ `::char5e/template`; `spell_subs.cljs:38-1235`, `equipment_subs.cljs:290-329`).
Each sub's handler is a pure function of its inputs; the facade re-expresses
the chain as ordinary function calls with the same bodies. This is the
largest piece of Clojure glue in the plan (a few hundred lines, mechanical),
and `compute.cljs` shows the pattern — it was extracted for exactly this
reason. Do it once, test it by comparing `buildTemplate(fixtures)` to what
the old app's subscriptions produce in a REPL.

Also lift: the built-in race/background/language definitions in the same
file (`spell_subs.cljs:514-928`) — they're plain `def`s and need no change.

## Wrinkles (from the engine investigation)

1. **`options.cljc` requires re-frame** (`options.cljc:26-27`) and a few
   modifiers read the global `re-frame.db/app-db` inside conditions (Dueling,
   `options.cljc:1739-1758`). The bundle carries re-frame as a dependency
   (small); the facade must either seed `app-db` with the keys those reads
   expect or patch the reads to take their input from the entity. Audit every
   `@re-frame.db/app-db` / `subscribe` / `dispatch` in `src/cljc` first —
   there are few. Patch (listed in `patches/`).
2. **No caching, lazy attributes.** Every attribute read re-runs its closure
   chain (`entity_spec.cljc:5-10`); the old UI debounces builds by 500 ms.
   The facade memoizes `evaluate` per entity value and converts the built
   character to a plain JS object **once** (extracting the ~100 accessors in
   `character.cljc:363-738` in one pass), so React never touches lazy
   ClojureScript values.
3. **Ordering matters.** Selection order in the entity drives modifier order;
   `from-strict-selections` uses `array-map` deliberately
   (`entity.cljc:168-171`). The facade must not round-trip entities through
   plain JS objects in a way that reorders keys; keep the entity as an
   opaque handle (or as the strict JSON with arrays, never key-ordered
   maps).
4. **Availability depends on the build.** `available-selections` takes the
   built character (prereqs). `evaluate` builds first, then resolves; the
   old `random-character` fixed-point loop (`events.cljs:310`, ≤10 rounds)
   becomes `autofill`.
5. **`ref` selections** store data at a global path and merge min/max across
   tree occurrences (`entity.cljc:423-514`). The facade exposes each
   selection's `actualPath` so the UI writes to the right place.
6. **Deferred values and multi-effect items.** Options with user values
   (ability scores, HP rolls, equipment quantity/equipped) resolve at build
   time; equipped magic items expand into several modifiers. All inherited —
   just don't strip `::entity/value` from options.
7. **Dead code to leave out of the facade**: the plugin patching system
   (`build-template`, `collect-plugins` — unused; live homebrew goes through
   `template-selections` arguments), `collect-modifiers` v1, memoized
   variants, the `#_`-disabled sourcebook blocks.
8. **Bundle size**: the content namespaces are ~2 MB of source. `:advanced`
   optimizations, async chunk + splash. Excluding `random.cljc` (56 KB),
   `templates/` (310 KB), and `monsters.cljc` from the *engine* chunk
   (monsters are never used by the character build — serve them as JSON for
   the browse page) helps.

## Patches to the engine source (the complete list, keep it short)

Commits to this fork (doc 00); each bumps the published package version.

| Patch | Why |
|---|---|
| Re-enable the legacy unnamespaced-key migration (`character.cljc:130-165`, currently `#_`) inside `importCharacter` | Contract R7 |
| Take the Dueling/app-db reads from the entity instead of `app-db` | Wrinkle 1 |
| Add `:boons` to `required-fields` and `content-type-names` in `import_validation.cljs` | Doc 01 known quirks |
| Extend `key-reference-map` to spells' `:spell-lists` and `level-selections` | Doc 01 known quirks |

Anything else is a facade concern, not an engine patch.

## Golden tests

Same strategy as Plan Set 1 doc 03, extended for the new scope:

1. Port `warlock_test.clj` and the three `character_test.clj` round-trip
   entities verbatim.
2. For each golden character (Plan Set 1 doc 01 §0.3 — add homebrew-using
   and legacy-quirk cases), assert `evaluate` matches values captured from
   the old app.
3. `buildTemplate` over each fixture `.orcbrew` matches the old
   subscription output (captured once from a REPL).
4. The C3 identity test: zero unresolved keys across all fixtures.

## Deliverables

- [ ] `engine-js/` in this fork with the build configuration above; the
      four patches committed; package published as `@dmv/engine`
- [ ] shadow-cljs `:esm` build, `^:export`ed facade, hand-written `.d.ts`
- [ ] De-re-framed `buildTemplate`; `evaluate` memoized with one-pass
      extraction
- [ ] Golden tests 1–4 green
