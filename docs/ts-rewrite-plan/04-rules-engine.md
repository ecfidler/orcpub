# 04 — The TypeScript Rules Engine

Design for the engine that replaces `entity.cljc` + `template_base.cljc` +
`modifiers.cljc`. Described in terms of the old engine's observed behavior
(engine investigation report) so the two can be compared attribute by
attribute.

## What the old engine does, in one paragraph

A **built character** is a map from attribute keyword to either a value or a
closure over the map. ~110 attributes are defined as closures in
`template_base.cljc` (`?armor-class`, `?prof-bonus`, `?spell-slots`, …), each
reading other attributes late-bound via `entity-val`. Selected options
contribute **modifiers**: functions that write one attribute (replacing the
closure with a value or accumulating onto it). `entity/build` collects all
modifiers from selected options, computes a dependency graph (which
attribute each modifier reads vs writes — determined at Clojure
macro-expansion time), **topologically sorts** the attributes, and folds the
modifiers over the base in that order. Conditions on modifiers are evaluated
against the partially built map mid-fold. `available-selections` then walks
the template, keeping only selections whose prereqs pass against the built
character — so availability depends on the build, and the UI iterates.

## The new engine, layer by layer

### 1. Attribute registry

Every computed attribute is declared once, with its derivation and its
dependencies made explicit (the old engine infers deps by scanning
`?symbols`; here they're written down and checked):

```ts
defineAttr("prof-bonus", {
  deps: ["total-levels", "proficiency-bonus-increase"],
  compute: (c) => Math.floor((c.get("total-levels") - 1) / 4) + 2 + c.get("proficiency-bonus-increase"),
});
defineAttr("traits", { accumulator: "list" });   // written only by effects
```

The full list to port is `template_base.cljc:35-328`. Group them (matching
the output-surface report): abilities & modifiers; proficiencies; combat
(AC family, HP, initiative, speeds, attacks, resistances); spellcasting;
features; equipment; levels. Non-obvious derivations that must match
exactly — treat each as a unit test:

| Attribute | Old derivation |
|---|---|
| `abilities` | per ability: `max(max(overrides), base + increases + level-increases)` (`template_base.cljc:89-102`) |
| `ability-bonus` | `floor(score/2) - 5` |
| `prof-bonus` | `floor((total-levels-1)/4) + 2 + increase` |
| `max-hit-points` | `max(1, level-increases + total-levels × per-level-bonus + Σ per-class(bonus × class-level))` |
| `armor-class-with-armor(armor, shield)` | `max(base-with-armor, …ac-formulas) + Σ ac-bonus-fns`; base: none/none → unarmored AC; none/shield → unarmored-with-shield; else `shield + armor-dex(armor) + armored-bonus + base-ac + magical` — `template_base.cljc:67-86` |
| `armor-dex-bonus(armor)` | light: dex; medium: `min(max-medium-dex (2), dex)`; heavy: 0 |
| best AC | cartesian `(equipped armor ∪ none) × (equipped shields ∪ none)`, max — computed in the UI/PDF, not the engine (`subs.cljs:750-764`); the new engine should own it |
| `skill-bonus` | expertise → 2×prof; prof → prof; else `max(default-skill-bonus-fns(ability))` (Jack of All Trades); + ability bonus + additional |
| `save-bonus` | ability bonus + save bonuses + (prof if proficient); proficiency from **first class only** |
| `spell-slots` | >1 slot factors → multiclass table by `total-spellcaster-levels = Σ floor(class-level/factor)`; exactly 1 → single-class table by that class's level and factor; then **merge-with +** the warlock pact table when `pact-magic?` (`template_base.cljc:285-299`; tables `options.cljc:488-587`, `template_base.cljc:13`) |
| `spell-save-dc(ability)` | `8 + prof + ability-bonus + spell-save-dc-bonus` |
| `prepare-spell-count(class)` | `ability-mod + floor(class-level / slot-factor)` |
| `weapon-attack-modifier(weapon, finesse?)` | prof (if proficient) + magical attack bonus + weapon attack bonus + melee/ranged bonus + ability mod + Σ attack-modifier-fns; ability: STR for melee-non-finesse and ranged-finesse, DEX otherwise (deliberate inversion, `template_base.cljc:188-197`) |
| `weapon-damage-modifier(weapon, finesse?, offhand?)` | magical damage bonus + ability mod (0 if offhand) + Σ damage-bonus-fns |
| `total-speed` | `max(speed, …speed-overrides)`; same pattern for flying/swimming/climbing |
| `num-attacks` | `max(extra-attacks, …number-of-attacks)` |
| `darkvision` | override with priority (`order`) + bonus |
| `passive-perception` | `10 + skill-bonus(perception)` + cumulative bonuses |
| `critical` | set, default `{20}` |

### 2. Effect application

Effects (doc 02) are applied in **dependency order**: build the graph from
each effect's `writes` attribute and `reads` set (declared per effect kind,
plus whatever its `Expr`/`Condition` reads), union with the attribute
registry's deps, topologically sort, and fold. Within one attribute, apply
in `order` then selection-traversal order (stable). This reproduces
`kahn-sort` + `order-modifiers` (`entity.cljc:254-266, 419`) without the
old engine's two failure modes: mis-detected deps (impossible here — they're
declared) and silent fallback on cycles (throw at content-load time).

Accumulator semantics per kind must match the old `cum-sum-mod` (numeric
add), `vec-mod` (append), `set-mod` (union), `map-mod` (assoc),
`modifier` (replace). Conditions are evaluated against the partial
character at application time, as the old fold does — this is observable
behavior (e.g. `saving-throw-proficiency` checks `first class` after
`classes` has been written).

Deferred values: options with `valueSchema` carry user input (ability
scores, HP rolls, equipment quantity/equipped, custom names). The effect
reads the option value at application time — the old `deferred-modifier`
(`modifiers.cljc:59`, resolution at `entity.cljc:590`). Magic items expand
into several effects when equipped (`deferred-magic-item-fn`,
`modifiers.cljc:467`).

### 3. Selection resolution (`available-selections`)

Port `entity/get-all-selections-aux-2` + `combine-ref-selections` +
`remove-disqualified-selections` (`entity.cljc:423-514`):

- Walk the selection tree; descend into an option only if it's selected.
- **Ref selections** merge: same `ref` path → one logical selection with
  `min` = Σ mins, `max` = Σ maxes (null if any null), options unioned by key;
  data lives at the ref path (contract C2).
- Drop selections whose `prereq` fails against the built character; grey
  out (or hide, if `hideIfFail`) options whose `prereqs` fail.
- `count-remaining` per selection: `max(0, min − selected)` where "selected"
  respects `requireValue`.

Availability depends on the build, and the build depends on selections, so
expose one call `evaluate(entity) → { built, selections }` that builds first
then resolves. The old app's `random-character` fixed-point loop
(`events.cljs:310`, max 10 rounds) is reproduced as `autofill(entity)`.

### 4. Entity model

Internal entity = the strict entity of contract C1, held immutably.
Mutations are pure functions: `select(entity, path, optionKey)`,
`deselect`, `setValue(entity, path, value)`, `setField(entity, key, value)`,
`addLevel(entity, classKey)`, `removeLevel`, `setClass`,
`addStartingEquipment` … — the old `event_handlers.cljc` and
`character.cljc:752-856` are the list. Each has a round-trip test (old
`event_handlers_test.clj` pattern).

Path semantics: `[selKey, optKey, selKey, optKey, …]`; multi-select indices
are not part of the path (`entity.cljc:299`); `actual-path` = `ref` if set.

### 5. Output surface

The engine exposes exactly the old accessor set (output-surface report §1 —
~100 accessors in `character.cljc:363-738`, mirrored in the
`character-subs` map at `subs.cljs:628-736`). Publish it as a typed
`BuiltCharacter` interface with the same names (camel-cased), grouped:
abilities, proficiencies, combat, spellcasting, features, equipment,
levels/identity, description fields. Stored values (`::char5e/*` — name,
XP, current HP, worn armor, attuned items, description fields) are exposed
alongside computed ones, but the implementation keeps them separate (the
old two-key-space split).

## Golden tests — the engine's contract

Three sources, in order of authority:

1. **Existing repo fixtures.** `test/cljc/orcpub/dnd/e5/warlock_test.clj`
   (level-10 Drow Warlock / Spy / Keen Mind: asserts abilities, race names,
   5 skill profs with counts, class level, speed, invocation-granted spells)
   and the three Datomic entities in `character_test.clj`. Port the
   assertions verbatim as the first TypeScript tests.
2. **Captured reference sheets.** For each golden character (Plan Set 1 doc
   01 §0.3 — extend to 4 levels × 12 classes + multiclass + homebrew cases),
   capture the old app's built values by evaluating the accessor list in a
   Clojure REPL against the saved entity, and store as JSON expected-output.
   A script in this repo produces them (`scripts/dump-built-character.clj`,
   to be written); the new engine must match every value.
3. **Property tests** for the pure math (AC combinations, slot tables for
   every multiclass combination of factors, prof bonus by level, ability
   modifiers).

Coverage gaps the old suite has and the new one closes: AC with
armor/shield combos, max HP, multiclass slots, save/skill totals, attack and
damage modifiers, spell DC/attack, and the PDF field map (doc 07).

## Performance

The old engine recomputes lazy attributes on every read with no caching and
needs a 500 ms debounce (`subs.cljs:310`). The new engine memoizes computed
attributes per build; a build for a level-20 multiclass character should be
well under 10 ms, removing the need for the debounce.

## Deliverables

- [ ] `packages/engine` with `evaluate`, `autofill`, the mutation functions,
      `BuiltCharacter`, and the content registry loader (doc 02 schema)
- [ ] Attribute registry with declared deps; cycle check on load
- [ ] Golden test suite from sources 1–3 above, green for the whole SRD
- [ ] Reference-dump script committed in *this* repo for regenerating
      expected outputs
