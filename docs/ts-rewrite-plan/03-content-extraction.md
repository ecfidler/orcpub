# 03 — Content Extraction

Getting the SRD content out of this repository and into the schema of doc 02.
Three tiers with three very different techniques, established by the data
inventory.

## Tier A — mechanical export (~1,000 entries, days of work)

These namespaces are pure EDN data with no functions inside entries. They
load cleanly in a JVM REPL because they don't depend on re-frame.

| Namespace | Entries | Notes for the exporter |
|---|---|---|
| `dnd/e5/spells.cljc` | 268 | 16 entries have explicit `:key`; derive the rest with `name-to-kw`. Inline the `def`'d string constants (`evocation`, `actions-1`, …). |
| `dnd/e5/monsters.cljc` | 317 | No `:key` in source; derive. `:challenge` uses Clojure ratios — emit number + display string. |
| `dnd/e5/weapons.cljc` | 40 + 5 ammunition | Keys are namespaced (`::weapon5e/damage-die`); strip namespaces. |
| `dnd/e5/armor.cljc` | 14 | Plain. |
| `dnd/e5/equipment.cljc` | ~160 | Grouped defs (tools, packs, gear, treasure) — flatten with a `category` field. Packs have `:items {key qty}`. |
| `dnd/e5/skills.cljc` | 18 | `:ability` is a namespaced keyword — strip. |
| `dnd/e5/spell_lists.cljc` | 8 classes, 787 refs | Class → level → spell keys. Verify every key resolves. |
| `dnd/e5/options.cljc` tables | conditions 15, damage types 13, alignments 9, abilities 6, xps 20, draconic ancestries 10, spell-slot schedules | Small literal tables near the top of the file and at `:488` (slot schedules by caster factor). |
| `dnd/e5/template_base.cljc:13` | warlock pact-slot table | 20-level map. |
| `spell_subs.cljs:546-577` | 16 languages | In a `.cljs` file — copy by hand (16 lines). |

Method: a one-off Clojure script (`lein run -m` or a REPL session) that
requires each namespace and writes `cheshire`/`data.json` output to
`content/srd/<type>.json`. Add a `--check` mode that asserts counts and that
all cross-references resolve (spell lists → spells, pack items → equipment,
monster keys unique).

**Do not** try to load `options`, `classes`, `template`, or `magic-items` in
this script — they require `re-frame` and won't load outside the browser
build.

## Tier B — magic items: data plus modifier translation (~1 week)

`dnd/e5/magic_items.cljc`: 259 literal entries + 29 generated
(armors/rings of resistance/vulnerability), expanded at runtime to several
hundred by cross-producting `+N weapon/armor` against base items.

- ~64% are pure data — export like Tier A (the file needs a shim to load
  without re-frame, or extract via regex/`rewrite-clj` as text).
- 93 entries carry code in one of three forms, each with a fixed translation:
  1. `::modifiers [(mod5e/...)]` — 53 entries, ~120 call sites across 26
     distinct constructors (`action` 13, `ability` 10, `damage-resistance` 6,
     `spell-attack-modifier-bonus` 4, `saving-throw-advantage` 4, …).
     Translate each call to its effect kind from doc 02. Do this by hand with
     a checklist; it's tedious but not hard.
  2. `::item-subtype heavy-metal-armor?` / `ammunition?` / `not-shield?` —
     64 uses of predicate functions selecting which base items an item
     applies to. Replace with a declarative filter: `{ armorType: ["medium",
     "heavy"], metal: true }` etc. (Three predicates total.)
  3. `:name-fn (plus-1-name :name)` — name templating for expanded items.
     Replace with a `nameTemplate: "{base}, +1"` string.
- Reproduce `expand-magic-items` (`magic_items.cljc:3077`) in TypeScript at
  load time rather than exporting the expanded list — keeps the bundle small
  and matches how keys like `:longsword-plus-1` are derived (verify the exact
  derived-key rule against `add-key` at `:2953` — contract C2).

## Tier C — hand re-authoring (the long pole, weeks)

Code, not data. Re-author against the doc 02 schema, using the Clojure
source as the specification and the reference app as the oracle.

### Re-authoring checklist

Exact inventory of what ships (from `content_reconciliation.cljs:157-210`
and the source):

| What | Count | Where in the old source |
|---|---|---|
| Classes | 12 | `classes.cljc` — `defn`s calling `opt5e/class-option` at lines 40, 243, 391, 750, 1046, 1222, 1419, 1765, 1972, 2200, 2373, 2988 |
| Subclasses (one per class) | 12 | nested in each class; keys `:berserker :lore :life :land :champion :open-hand :devotion :hunter :thief :draconic :fiend :evocation` |
| Eldritch invocations | 33 | `classes.cljc:2677-2952` (name + description + prereq + some effects) |
| Pact boons | 3 | `classes.cljc:2629-2676` |
| Fighting styles | 6 | `options.cljc:1717-1780` |
| Monk elemental disciplines | — | `options.cljc:274-380` — **Four Elements is not the SRD monk subclass**; skip unless Open Hand needs shared code |
| Races | 9 | `spell_subs.cljs:625-897` (dwarf, elf, halfling, human, dragonborn, gnome, half-elf, half-orc, tiefling) |
| Subraces | ~20 | nested; one active per race plus 9 human cultural variants (the "human subraces" are name-only variants) |
| Backgrounds | 1 | Acolyte — `spell_subs.cljs:514` |
| Feats | 1 | Grappler — `options.cljc:1289` |
| The character-math base | ~110 derived attributes | `template_base.cljc:35-328` — this is the *engine*, not content; doc 04 |
| Starting-equipment selections | per class/background | `opt5e/new-starting-equipment-selection` call sites (28) |

Everything else in `classes.cljc`, `options.cljc`, `template.cljc`, and the
whole `templates/` directory is `#_`-disabled or unreferenced non-SRD content
and is **excluded**.

### Process per class

1. Read the class `defn` top to bottom; list every `mod5e/*`, `mod/*`, and
   `opt5e/*` call and every `?attribute` read.
2. For each, find the effect kind / condition / expression in doc 02. If none
   fits, **extend the schema** (that is the schema's acceptance test) rather
   than special-casing.
3. Write the JSON class record.
4. Build a level-1, level-5, level-11, level-20 instance of the class in the
   reference app, capture the sheet values, and add them as golden tests
   (doc 04) before moving to the next class.

Suggested order: Fighter (simplest; validates equipment and fighting styles),
Barbarian (unarmored defense, rage tables), Rogue (expertise, sneak attack
scaling), Cleric (prepared casting, domain spells), Wizard (spellbook,
known-mode), Warlock (pact magic, invocations — the existing
`warlock_test.clj` is the oracle), then Bard, Druid, Monk, Paladin, Ranger,
Sorcerer.

### Races

Half data, half code. `:abilities`, `:size`, `:speed`, `:languages`,
`:darkvision`, `:weapon-proficiencies`, `:traits` are plain fields;
`:modifiers` and `:selections` are calls (`saving-throw-advantage`,
`immunity`, `skill-proficiency`, `tool-selection`, `language-selection`,
`hit-point-level-bonus`). Author them in the **orcbrew race shape** (doc 06)
— which is also the doc 02 race record — so SRD races and homebrew races are
literally the same record type.

## Name tables — a licensing decision, not a technical one

`dnd/e5/character/random.cljc` holds ~2,155 name strings: Forgotten Realms
ethnic name tables (PHB chapter 2, **non-SRD**) plus 553 tavern names that
appear original. Do not port the FR tables. Options: (a) omit random names,
(b) write original name lists, (c) use a permissively-licensed generator.
The tavern names are likely fine to keep; confirm provenance first.

## Verification of the extraction

- Counts match the table above exactly.
- Every key referenced anywhere (spell lists, packs, subclass → class, race →
  subrace, magic item → base item) resolves in the registry.
- Every key equals the old app's key for the same entity — checked by
  a script that walks the old app's built template (from a REPL) and diffs
  the set of `(selection-key, option-key)` pairs against the new registry.
  This is the mechanical proof of contract C2.
