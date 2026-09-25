# 02: The engine library

Plan Set 1 doc 03 (`docs/ts-frontend-plan/03-engine-package.md`) describes
the base: a shadow-cljs build of the `.cljc` core behind a small typed
facade with `buildCharacter`, `availableSelections`, `selectOption`,
`setValue`, `randomCharacter`, and a few more. This document covers what a
new app needs on top of that, what the engine investigation says a facade
author must know, and how the build is scoped. Doc 00 covers where the
build lives.

## Where the engine is built

Doc 00 supersedes the original text of this section: the engine is built
and published from this fork, not vendored into the app repo. The build is
at `engine-js/` beside `src/`, the Plan Set 1 doc 03 layout, and compiles
the engine namespaces in place:

- All of `src/cljc/orcpub/**`. This includes `compute.cljc`, the pure
  helpers already extracted from the subscriptions.
- From `src/cljs/orcpub/dnd/e5/`: `spell_subs.cljs` (the built-in races,
  backgrounds, and languages, plus the pipeline from homebrew to template),
  `import_validation.cljs` (`.orcbrew` parsing, cleaning, and validation),
  and `content_reconciliation.cljs` (missing-content detection).
- `engine-js/src/orcpub/facade.cljs`, the exported API.

The build excludes these on purpose:

- `pdf_spec.cljc`: there is no PDF feature.
- `character/random.cljc`: non-SRD name tables (doc 01).
- `char_decision_tree.cljc`: it depends on the random names, and the "newb"
  builder is out of scope.
- Everything under `templates/`: unreferenced and non-SRD.
- All of `src/clj`.

The four patches listed below are ordinary commits to this fork's source.
The app repo consumes the published `@dmv/pubdoor` package and never sees
Clojure.

## Facade API beyond Plan Set 1 doc 03

| Facade function | Backed by | Notes |
|---|---|---|
| `evaluate(entity, { rules, homebrew })`, returning `{ built, selections }` | `entity/build`, `entity/available-selections`, and the template built from `t5e/template` with homebrew merged in | Replaces the old subscription chain. One call, memoized on `(entity, homebrewVersion)`. `rules` defaults to `"2014"`, and any other value throws (see §Rules edition) |
| The mutations: `select`, `deselect`, `setValue`, `setField`, `addLevel`, `removeLevel`, `setClass`, `addStartingEquipment`, and the rest | `event_handlers.cljc`, `character.cljc:765-869` | Pure. Each has an old round-trip test to port |
| `importCharacter(transitOrEdn)`, returning an entity | `char5e/from-strict` plus the R5 and R7 additions from doc 01 | See doc 03 |
| `exportCharacter(entity)`, returning JSON | `char5e/to-strict` | The new app's own file format (doc 03) |
| `buildTemplate(homebrew)` | The `spell_subs.cljs` chain, lifted to functions | See §De-re-framing below |
| `parseOrcbrew(text)`, returning `{ data, log, conflicts }` | `import_validation/validate-import` and its helpers | Pure. See doc 04 |
| `orcbrewToEdn(plugins)` and `prettyEdn` | `pr-str` and `pprint` | Export |
| `reconcileMissingContent(entity, homebrew)` | `content_reconciliation.cljs` | Suggestions for unresolved keys |
| `content.spells()`, `monsters()`, `magicItems()`, `weapons()`, and the rest | The data namespaces plus the `magic-items` expansion | Plain JS lists for the browse pages. Consider a build-time JSON dump instead, so those pages can be split away from the engine chunk |
| `keys.selectionKeys()` and `optionKeys()` | A walk of the built template | For the C3 identity test |

## Rules edition

The facade names the rules edition although only one exists, so that a
later 2024 engine can implement the same interface (option E in
`docs/reports/2024-rules-support.md`, ORC-94).

- `evaluate` takes `rules` in its options. It defaults to `"2014"`, the
  only value 0.1 accepts, and any other value throws an error that names
  the edition (ORC-16).
- `types/index.d.ts` exports `type Rules = "2014"`. It splits
  `BuiltCharacter` into an edition-neutral sheet part and a `Built2014`
  extension with the 2014-shaped keys, such as `race-ability-increases`,
  `spell-slot-factors`, and `pact-magic?` (ORC-24). The raw `built` object
  is unchanged. The split is in the types only.

Neither change touches the engine source, so neither is a patch.

## De-re-framing `spell_subs.cljs`

De-re-framing means rewriting re-frame subscriptions as plain functions.
The conversion from homebrew to template is a chain of `reg-sub` calls:
`::e5/plugins`, then `plugin-vals`, then `plugin-races`, `plugin-classes`,
and their siblings, then `::races5e/races`, `::classes5e/classes`, and
their siblings, then `::char5e/template-selections`, and finally
`::char5e/template` (`spell_subs.cljs:38-1235`,
`equipment_subs.cljs:290-329`). Each subscription's handler is a pure
function of its inputs, so the facade re-expresses the chain as ordinary
function calls with the same bodies. This is the largest piece of Clojure
glue in the plan, a few hundred mechanical lines. `dnd/e5/compute.cljc`
shows the pattern. It was extracted for exactly this reason. Do it once,
and test it by comparing `buildTemplate(fixtures)` to what the old app's
subscriptions produce in a REPL.

Also lift the built-in race, background, and language definitions in the
same file (`spell_subs.cljs:514-928`). They are plain `def` forms and need
no change.

## Wrinkles: engine behaviors the facade must work around

The engine investigation found eight behaviors, called wrinkles in this
plan, that a facade author must work around.

1. **`options.cljc` requires re-frame** (`options.cljc:26-27`), and a few
   modifiers read the global `re-frame.db/app-db` inside conditions, for
   example Dueling (`options.cljc:1739-1758`). The bundle carries re-frame
   as a dependency, which is small. The facade must either seed `app-db`
   with the keys those reads expect or patch the reads to take their input
   from the entity. First audit every `@re-frame.db/app-db`, `subscribe`,
   and `dispatch` in `src/cljc`. There are few. Then patch them (D2 in the
   patch list below).
2. **Lazy attributes with no caching.** Every attribute read re-runs its
   closure chain (`entity_spec.cljc:5-10`), which is why the old UI
   debounces builds by 500 ms. The facade memoizes `evaluate` per entity
   value and converts the built character to a plain JS object once,
   extracting the roughly 100 accessors in `character.cljc:376-751` in one
   pass, so React never touches lazy ClojureScript values.
3. **Ordering matters.** Selection order in the entity drives modifier
   order, and `from-strict-selections` uses `array-map` deliberately
   (`entity.cljc:168-171`). The facade must not round-trip entities through
   plain JS objects in a way that reorders keys. Keep the entity as an
   opaque handle, or as the strict JSON with arrays, never as key-ordered
   maps.
4. **Availability depends on the build.** `available-selections` takes the
   built character, because prerequisites depend on it. `evaluate` builds
   first, then resolves. The old `random-character` fixed-point loop
   (`events.cljs:310`, at most 10 rounds) becomes `autofill`.
5. **`ref` selections** store data at a global path and merge `min` and
   `max` across tree occurrences (`entity.cljc:423-514`). The facade
   exposes each selection's `actualPath` so the UI writes to the right
   place.
6. **Deferred values and multi-effect items.** Options with user values,
   such as ability scores, HP rolls, and equipment quantity and equipped
   state, resolve at build time. Equipped magic items expand into several
   modifiers. All of this is inherited. Do not strip `::entity/value` from
   options.
7. **Dead code to leave out of the facade.** The plugin patching system
   (`build-template` and `collect-plugins`) is unused, because live
   homebrew goes through `template-selections` arguments. Also leave out
   `collect-modifiers` v1, the memoized variants, and the sourcebook blocks
   disabled with `#_`.
8. **Bundle size.** The content namespaces are about 2 MB of source. Use
   `:advanced` optimizations, and load the engine as an async chunk behind
   a splash screen. Excluding `random.cljc` (about 56 KB), `templates/`
   (about 310 KB), and `monsters.cljc` from the engine chunk also helps.
   The character build never uses monsters, so serve them as JSON for the
   browse page.

## Patches to the engine source

This is the complete list. Keep it short. Each patch is a commit to this
fork (doc 00) and bumps the published package version.

| Patch | What | Why |
|---|---|---|
| D1 | Re-enable the legacy unnamespaced-key migration (`character.cljc:121-178`, formerly disabled with `#_`) inside `importCharacter`. Done in ORC-20, which also fixed `add-custom-equipment-namespaces` and made the ability-score step conditional, to match `orcpub.oracle/legacy-normalize` | Quirk R7 |
| D2 | Take the Dueling reads from the entity instead of `app-db`. Fix the `(fn [weapon _] …)` arity, which fails only in JS, and document that the bonus applies only with a one-handed melee weapon in the main hand and a non-weapon such as a shield in the off hand (`fixtures/README.md` finding 2) | Wrinkle 1 |
| D3 | Add `:boons` to `required-fields` and `content-type-names` in `import_validation.cljs` | Doc 01 §Known quirks |
| D4 | Extend `key-reference-map` to spells' `:spell-lists` and `level-selections` | Doc 01 §Known quirks |

Anything else is a facade concern, not an engine patch.

## Golden tests

The strategy is the same as Plan Set 1 doc 03, extended for the new scope:

1. Port `warlock_test.clj` and the three `character_test.clj` round-trip
   entities verbatim.
2. For each golden character (Plan Set 1 doc 01 §0.3, plus homebrew and
   legacy-quirk cases), assert that `evaluate` matches the values captured
   from the old app.
3. `buildTemplate` over each fixture `.orcbrew` matches the old
   subscription output, captured once from a REPL.
4. The C3 identity test: zero unresolved keys across all fixtures.
5. The differential corpus: characters generated with the mutations and
   `autofill`, dumped with their built values under `fixtures/corpus/`
   (ORC-98). It is not an M1 deliverable. It is the acceptance suite for
   the later engine's 2014 port (ORC-97).

## Deliverables

Linear project PubDoor tracks the deliverables in two milestones. M1
(ORC-15 to ORC-26) covers the scaffold, `evaluate`, the golden tests, the
mutations, `importCharacter`, patches D1 and D2, `autofill`, the types, CI,
and publishing 0.1.0. M3 (ORC-27 to ORC-43) covers `buildTemplate`,
`parseOrcbrew`, export, reconciliation, patches D3 and D4, the content
lists, the C3 identity test, bundle size, and publishing 0.2.0.
