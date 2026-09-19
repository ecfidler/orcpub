# 02 — The Content Schema

The single most important design decision in the rewrite. The old engine
expresses mechanics as Clojure code (macro-expanded closures reading lazy
entity attributes). The new engine must express the *same* mechanics as
**data** that a TypeScript evaluator runs — because (a) that's the only way
to ship SRD content without a Clojure toolchain, and (b) homebrew content is
already data, and the two must be the same thing.

## Design goals

1. **One format for everything.** SRD classes and homebrew classes are the
   same record type. There is no "built-in" special case.
2. **Superset of the `.orcbrew` vocabulary.** Every mechanic a homebrew file
   can express (`:props`, `:level-modifiers`, `:level-selections`,
   `:spellcasting`, `:traits`, `:profs`, `:spells`, `:abilities`) maps to the
   schema 1:1, so import is a key-renaming pass. See doc 06 for the mapping.
3. **Expressive enough for the 12 SRD classes.** The old code's escape hatch
   is arbitrary Clojure (`(mod/modifier ?x (complex expr))`). The schema needs
   a small, closed set of *effect kinds* plus a small *expression language*
   for level-scaled and ability-derived values. Doc 03 §"Re-authoring
   checklist" is the acceptance test: if a class can't be written, the
   schema is missing something.
4. **Stable keys.** Every entity's `key` is its old-app keyword (contract C2).

## Entity types

Thirteen content types, matching the orcbrew set plus magic items (which the
old app stores server-side but which are content all the same):

`race`, `subrace`, `class`, `subclass`, `background`, `feat`, `spell`,
`monster`, `magic-item`, `language`, `invocation`, `boon`, `selection`,
`encounter` — plus the reference data that isn't user-authorable: `weapon`,
`armor`, `equipment`, `skill`, `condition`, `damage-type`, `alignment`,
`spell-list`.

Every entity: `{ key: string; name: string; source: string; disabled?: boolean; ... }`.
`source` is the pack name (`option-pack` in orcbrew; `"srd"` for built-ins).

## The effect vocabulary

Effects are what an option contributes when selected. They are the data form
of the old modifier constructors (`dnd/e5/modifiers.cljc`, catalogued in the
engine investigation). Each effect has a `kind` and writes one *attribute*
of the character. The full list, grouped by the attribute family it writes:

| Family | Effect kinds (old constructor in parentheses) |
|---|---|
| Identity | `class` (`cls`), `subclass`, `subclass-name`, `race`, `subrace`, `background`, `alignment`, `size`, `level` |
| Abilities | `ability` (+n to one ability), `ability-override` (set to n, max wins), `level-ability-increase`, `base-abilities` (the score map), `race-ability`/`subrace-ability` (bookkeeping variants) |
| Saves | `saving-throw-proficiency` (list, first-class-only condition), `saving-throw-bonus`, `saving-throw-bonus-all`, `saving-throw-advantage` |
| Skills/tools | `skill-proficiency`, `skill-expertise`, `skill-bonus`, `all-skills-bonus`, `tool-proficiency`, `tool-expertise`, `default-skill-bonus` (Jack of All Trades), `passive-perception`, `passive-investigation` |
| Proficiencies | `weapon-proficiency`, `armor-proficiency` (light/medium/heavy/shields), `language` |
| Senses/speed | `darkvision` (override w/ priority), `darkvision-bonus`, `speed`, `speed-override`, `flying-speed[-override\|-equals-walking]`, `swimming-speed[...]`, `climbing-speed[...]`, `unarmored-speed-bonus` |
| Defenses | `damage-resistance`, `damage-vulnerability`, `damage-immunity`, `condition-immunity`, `immunity` (each `{value, qualifier?}`) |
| AC | `armored-ac-bonus`, `unarmored-ac-bonus`, `natural-ac-bonus`, `magical-ac-bonus`, `unarmored-defense` (class key; first wins), `ac-formula` (see expressions), `medium-armor-max-dex` |
| HP | `max-hp-bonus` (flat), `hp-per-level-bonus` (all classes), `class-hp-per-level-bonus` (one class), `hp-level-value` (rolled/average per level — deferred, from option value) |
| Attacks | `extra-attack`, `num-attacks`, `attack-bonus` (with weapon filter), `damage-bonus` (with weapon filter), `ranged-attack-bonus`, `critical-range`, `dual-wield-any-one-handed`, `two-weapon-ac-bonus` |
| Spellcasting | `spell-slot-factor` (class → 1/2/3), `pact-magic`, `spells-known` (spell, level gate, ability, class, always-prepared?), `spells-known-mode`, `prepares-spells`, `spell-save-dc-bonus`, `spell-attack-bonus` |
| Features | `trait` (name, description, level gate, class gate, type ∈ passive/action/bonus-action/reaction, summary, frequency), `attack` (structured attack block), `action`/`bonus-action`/`reaction` (trait sugar) |
| Equipment | `weapon`, `armor`, `equipment`, `treasure`, `magic-item` grants (quantity, equipped) — deferred from option value |
| Bookkeeping | `initiative`, `proficiency-bonus-increase`, `option-source`, `al-illegal`, `used-resource` |

Rule of thumb from the investigation: the old `mods-map` (21 kinds) +
orcbrew `:props` (~30 kinds) + `:level-modifiers` (12 kinds) already cover
the majority of *homebrew* needs; the remaining ~40 kinds exist for SRD class
features. All go in one table.

### Effect shape

```ts
interface Effect {
  kind: EffectKind;
  // kind-specific payload, e.g. { ability: "str", bonus: 2 }
  ...payload;
  when?: Condition;        // optional gate (see below)
  order?: number;          // tie-break within an attribute (darkvision priority)
}
```

### Conditions and gates

The old engine gates most features inline: `(>= (?class-level :monk) 5)`,
`(= :barbarian (first ?unarmored-defense))`, `(= cls-kw (first ?classes))`.
The schema needs a closed condition set:

```ts
type Condition =
  | { classLevel: ClassKey; min: number }
  | { totalLevel: { min: number } }
  | { firstClass: ClassKey }
  | { hasSubclass: SubclassKey }
  | { abilityAtLeast: { ability: Ability; value: number } }
  | { hasFeat: FeatKey } | { hasRace: RaceKey }
  | { unarmoredDefenseIs: ClassKey }
  | { equipped: { armorType?: "none"|"light"|"medium"|"heavy"; shield?: boolean } }
  | { all: Condition[] } | { any: Condition[] } | { not: Condition };
```

Level gating is so common it also gets sugar on the container: a class's
`levels[n].effects` are implicitly `classLevel ≥ n`, and a trait's `level`
field is the same gate — exactly how `trait-cfg` and `level-option` work.

### Value expressions

Some effect payloads are computed, not constant: Rage damage `{9: 3, 16: 4,
default: 2}` by level, Unarmored Defense `10 + dex + con`, Martial Arts die
by level, `prof-bonus`-scaled bonuses. The old code uses `level-val` tables
and arbitrary arithmetic over `?attributes`. The schema gets a tiny
expression language:

```ts
type Expr =
  | number
  | { attr: "prof-bonus" | "total-levels" | "str-mod" | ... }   // read a computed attribute
  | { classLevel: ClassKey }
  | { levelTable: { [level: number]: number; default: number }, of?: Expr } // level-val
  | { add: Expr[] } | { mul: Expr[] } | { max: Expr[] } | { min: Expr[] }
  | { floorDiv: [Expr, number] } | { ceilDiv: [Expr, number] };
```

This is deliberately not Turing-complete. The evaluator declares which
attributes each expression *reads*, which is what the engine needs for
dependency ordering (doc 04). If a class feature needs something outside
this language, the answer is a new effect kind, not a bigger language.

## Selections (choices)

Options and selections form the same alternating tree as the old template
(`t/selection-cfg` / `t/option-cfg` — every attribute is listed in the
engine investigation §1.2–1.3). Schema:

```ts
interface Selection {
  key: string; name: string;
  min?: number; max?: number | null;   // null = unlimited
  sequential?: boolean;                // class levels
  multiselect?: boolean;
  ref?: string[];                      // global storage path (contract C2)
  tags?: string[];                     // builder page routing
  requireValue?: boolean;
  different?: boolean;                 // ASI must pick distinct abilities
  prereq?: Condition;                  // hides whole selection
  options: Option[] | { from: OptionSource };   // static list or a query
}
interface Option {
  key: string; name: string; help?: string; order?: number;
  effects?: Effect[];
  selections?: Selection[];            // nesting
  prereqs?: { label: string; when: Condition; hideIfFail?: boolean }[];
  associatedOptions?: RawOptionFragment[];   // starting-equipment style side grants
  valueSchema?: "int" | "abilityMap" | "equipmentCfg" | "string"; // deferred-value options
}
```

`OptionSource` queries replace the old app's lazy option seqs: `{ from:
"spells", where: { level: 0, list: "wizard" } }`, `{ from: "languages" }`,
`{ from: "feats" }`. This is how a selection's options grow when homebrew is
loaded, without plugin patching.

## The class record (worked example)

The shape that must encode all 12 SRD classes **and** exactly mirror the
orcbrew class fields (doc 06 has the field-by-field mapping):

```ts
interface ClassDef {
  key: ClassKey; name: string; source: string;
  hitDie: 6 | 8 | 10 | 12;
  abilityIncreaseLevels: number[];             // [4,8,12,16,19]
  profs: {
    save: Ability[];                           // first-class only
    armor: ArmorProfKey[]; weapon: WeaponProfKey[]; tool?: ToolKey[];
    skillOptions: { choose: number; options: SkillKey[] };
    multiclass?: { armor?, weapon?, skillOptions?, tool? };
  };
  multiclassPrereqs?: Condition;
  startingEquipment: { weapons?, armor?, equipment?; choices: EquipmentChoice[] };
  effects?: Effect[];                          // class-level, ungated
  levels: { [n: number]: { effects?: Effect[]; selections?: Selection[] } };
  traits: Trait[];                             // {name, level, description, type, page}
  subclassLevel: number; subclassTitle: string; // selection key = kebab(subclassTitle)
  spellcasting?: Spellcasting;                 // orcbrew shape, verbatim
}
```

`Spellcasting` is taken directly from orcbrew (`level-factor`, `ability`,
`known-mode`, `spells-known`, `cantrips-known`, `spell-list-kw` |
`spell-list`, `prepares-spells?`) — the old `class-option` already turns
that record into the right modifiers (`options.cljc:2892-2977`), which tells
us the record is sufficient.

## Serialization

- Canonical on-disk format is **JSON** with string keys; keyword-ness is
  implied by field (keys, ability names, damage types are enumerated
  strings). Ratios (monster CR `1/8`) become numbers plus a display string.
- `.orcbrew` EDN is an *import/export* format, converted at the boundary
  (doc 06). Internally there is one representation.
- Schema is defined once as TypeScript types **and** as a JSON Schema (via
  a generator such as `typescript-json-schema` or zod), used to validate both
  the SRD bundle at build time and homebrew at import time.

## Validation of the schema design

The schema is done when:

- [ ] All 12 SRD classes + 12 subclasses are expressed (doc 03 checklist)
- [ ] All 9 races / ~20 subraces, 6 fighting styles, 33 invocations, Grappler,
      Acolyte are expressed
- [ ] The 93 magic items with modifiers are expressed
- [ ] Both `.orcbrew` fixtures and every community pack gathered in Phase 0
      import losslessly (doc 06 defines "lossless")
- [ ] The evaluator's dependency analysis produces an acyclic attribute
      graph for the full SRD bundle
