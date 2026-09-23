# Supporting the 2024 rules: options for the engine library

> Status: decided 2026-09-23. The owner chose option E (ORC-94). The
> *Decision* section records the answers and what follows from them. The
> analysis below it is kept as written on 2026-09-22, before M1 (ORC-15 to
> ORC-26) started. The plan itself is Plan Set 2, `docs/ts-rewrite-plan/`.

This report asks two things. Could a future engine library support both
D&D 5e 2014 (SRD 5.1, what orcpub implements) and D&D 5e 2024 (SRD 5.2)?
And should the plan change now, before M1 starts, to prepare for that?

The rules facts come from `docs/kb/srd-5.2-rules-delta.md`, which cites
the SRD documents and other primary sources. The engine facts come from a
survey of this repository. File and line citations are for commit
`6248213`.

## Summary

- **A dual-edition engine is viable.** The engine's generic core, about
  1,200 lines in `entity.cljc`, `template.cljc`, `modifiers.cljc`,
  `entity_spec.cljc`, and `entity/strict.cljc`, knows nothing about D&D. A
  second rules edition can run on the same core, in ClojureScript or in a
  TypeScript port of it.
- **The hard part is 2014, not 2024.** Supporting 2024 is new design work
  with no legacy data to match. Replacing the compiled engine for 2014 is
  the expensive part, because the plan's definition of done requires every
  imported character to evaluate to the same sheet values. The decision
  record put that re-authoring at 6 to 9 months. A dual-edition rewrite
  started now pays that cost before users see anything.
- **2024 does not fit the current engine as data alone.** Spells,
  species, subclass levels, and languages are new data. Backgrounds that
  grant ability scores and a feat, feat categories with level
  prerequisites, Weapon Mastery, and fixed prepared-spell counts need new
  engine concepts. Content keys also collide: a 2024 `:fighter`, `:elf`,
  or `:fireball` silently replaces the 2014 one
  (`spell_subs.cljs:976-979`, `:1185-1193`).
- **Every project surveyed that supports both editions tags each document
  with its edition and keeps the content sets apart.** Foundry VTT's
  `dnd5e` system, 5e-bits, Open5e, and Charnik all do. The cheapest
  preparation is the same: tag the edition in every document the new app
  writes.
- **Recommendation.** Keep M1 as planned, and make five small changes
  during M1 and M2 so that the app can add a second engine later (see
  *Changes to make during M1 and M2*). Build 2024 support as a new
  TypeScript engine after the 2014 app works, reusing the choice-tree
  model of the current core. Port 2014 onto that engine last, with
  `@dmv/pubdoor` as the test oracle. This is option E. One answer changes
  the recommendation: if a 2024 character must use 2014 content from the
  first release, option C is the better path. Option C is a second rules
  edition written in ClojureScript on the compiled engine.

## Decision (2026-09-23): option E

The owner chose option E. The compiled engine, `@dmv/pubdoor`, serves 2014
rules. A later TypeScript engine adds 2024 rules, and 2014 moves onto it
last so that the Clojure build can be retired. The answers to *Questions to
answer before deciding*:

1. **Side by side only (level 1).** A 2024 character uses only 2024
   content. A user who wants 2014 content in a 2024 character re-enters it
   as 2024 homebrew. The engine does not implement the 2024 Player's
   Handbook rules for older species and backgrounds.
2. **The 2014 port may change values.** When 2014 moves to the new engine,
   every character must import and evaluate, and a difference report
   against `@dmv/pubdoor` lists every sheet value that changed. Identical
   values stay required for as long as `@dmv/pubdoor` evaluates 2014
   characters.
3. **Clojure is temporary.** Working in it now is acceptable, for fast
   content and data compatibility. Leaving it is the end goal, so option C
   is out and the 2014 port is committed work.
4. **2024 content gets a new data format**, for built-in content and
   homebrew alike. `.orcbrew` stays the 2014 format.
5. **2014 comes first.** 2024 work starts after Alchemy 5e M5.
6. **No conversion.** A saved 2014 character is never converted to 2024.
7. **The engine model is open.** Whether the 2024 engine keeps the strict
   entity, the template of selections and options, and modifiers with
   declared dependencies is decided by the spike.

What follows from the answers:

- The five changes in *Changes to make during M1 and M2* are adopted. They
  are in ORC-16, ORC-24, ORC-48, ORC-49, ORC-50, ORC-53, and ORC-87, and
  the differential corpus is ORC-98.
- The spike drops its ClojureScript half, because option C is out. It
  ports the generic core to TypeScript and builds the 2024 Fighter slice
  (ORC-95).
- Option E no longer needs the `.orcbrew` vocabulary for mixing, because
  there is no mixing. The 2014 port still ports that vocabulary so that
  old homebrew keeps working (ORC-97).
- The 2024 content format and key scheme are a decision after the spike
  (ORC-96).
- Linear project *2024 engine* tracks ORC-95 to ORC-97.

## What "support both editions" can mean

The requirement has three levels. Each one costs more than the one above
it, and the options differ most on levels 2 and 3.

| Level | What the user can do | Example |
|---|---|---|
| 1. Side by side | Each character is 2014 or 2024. Content does not cross | A 2024 Fighter uses only 2024 species, backgrounds, feats, and spells |
| 2. Official mixing | A 2024 character uses 2014 content under the 2024 Player's Handbook rules for older content | A 2024 character with a 2014 race, whose ability increases are ignored |
| 3. Homebrew mixing | A 2024 character uses the user's existing 2014 `.orcbrew` content | The owner's 29-pack `all-content.orcbrew`, all of it 2014-era, applied to 2024 characters |

Converting a saved 2014 character into a 2024 character is a fourth
possibility. No option does it automatically, because the two editions ask
for different choices at different levels. It is a guided rebuild at best.
Charnik, one of the projects surveyed, leaves conversion out of scope.

## What 2024 changes, sorted by engine impact

SRD 5.2.1 has the same 12 classes and 12 subclasses as SRD 5.1, with four
of the subclasses renamed. It has 9 species, 4 backgrounds, 17 feats, 339
spells, and 8 Weapon Mastery properties. SRD 5.1 has 9 races, 1
background, 1 feat, and 319 spells. Page references for everything below
are in `docs/kb/srd-5.2-rules-delta.md`.

The two tables sort the changes by what they need from an engine built
like this one. The first holds changes that the current model can express
as new content. The second holds changes that need a new constructor, a
new formula in `template_base.cljc`, or new `.orcbrew` fields.

### Changes that fit the current model as new data

| 2024 change | What the engine needs | The 2014 engine today |
|---|---|---|
| 339 spells: 20 new, 2 renamed, 23 with a new school, and same-name spells with new mechanics (Cure Wounds is 2d8) | New spell data. Same-name spells need keys scoped by edition | Spell maps in `spells.cljc`, lists in `spell_lists.cljc` |
| Species with lineage, legacy, or ancestry choices, size as a choice for some species, and traits that scale with character level | A lineage is a nested selection, like a subrace | `race-option` with nested selections |
| Every subclass at level 3, and new level-1 choices such as Divine Order and Primal Order | Class data | `:subclass-level` and per-level `:selections` |
| Warlock invocations from level 1, pact boons as invocations, and invocations that require other invocations | Invocation data | Trait-name prerequisites already exist (`options.cljc:3104-3111`) |
| Languages: Common plus two choices, Draconic becomes standard, and species grant none | One top-level language selection | `language-selection` with `:ref [:languages]` |
| Weapon and armor table changes, and armor renamed ("Padded Armor") | Item data. A renamed item gets a new key | `weapons.cljc`, `armor.cljc` |
| Full-caster and Pact Magic slot tables, hit points, the proficiency bonus, passive Perception, and carrying capacity | Nothing. These are unchanged | |

### Changes that need new engine concepts

| 2024 change | What the engine needs | The 2014 engine today |
|---|---|---|
| The background grants +2 and +1, or +1 to three, among three listed abilities, plus an Origin feat | A new background constructor, and an ability selection with a per-ability cap | `background-option` has no ability or feat field (`options.cljc:2453-2506`) |
| Feat categories (Origin, General, Fighting Style, Epic Boon), level and class-feature prerequisites, repeatable feats, the Ability Score Improvement as a feat, and a cap of 30 for Epic Boons | A new feat model, and feat selections filtered by category | `feat-prereqs` handles ability 13, spellcasting, armor, and race (`options.cljc:3190-3217`). All feats share one `:ref [:feats]` pool |
| An Epic Boon at level 19 for every class, and Fighting Style as a feat that can change on each level | Category-filtered feat selections inside class levels | "ASI or feat" at `:ability-increase-levels`. A fixed fighting-style list (`options.cljc:1717-1778`) |
| Weapon Mastery: 2 to 6 weapon kinds by class and level, changeable on a Long Rest, one mastery property per weapon | A selection over weapon kinds, a weapon property, and attack output that reports the mastery | Weapons are open maps. Nothing reads a mastery key |
| Every caster prepares a fixed number of spells from a table, and the swap rules differ by class | A prepared-count table in place of the formula and the three "known" modes | `:known-mode`, and the ability modifier plus `level / factor` (`template_base.cljc:274-284`) |
| Paladins and Rangers get slots at level 1, and the multiclass caster level rounds their half levels up | A new half-caster table and multiclass formula | The factor-2 table starts at level 2, and `int` truncates (`template_base.cljc:263-269`) |
| Starting equipment is a whole package or gold, per class and per background | A new equipment-choice shape. The `addStartingEquipment` mutation (ORC-19) changes meaning | Item-by-item choices (`event_handlers.cljc:37-43`) |
| Wild Shape keeps a list of known beast forms | A selection over monsters. Plan Set 2 keeps monsters out of the engine chunk (doc 02 wrinkle 8) | A trait with a CR limit (`classes.cljc:781-801`) |

### The rules for mixing are not verified yet

The 2024 Player's Handbook reportedly says how to use older species and
backgrounds. An older species' ability increases are ignored. An older
background gets the new ability choice, plus an Origin feat if it has none.
The research note could not verify this wording, because the book is not
free and D&D Beyond was unreachable. SRD 5.2.1 has no general rule. It has
only a few notes, such as the one in Blessed Strikes about "a Cleric
subclass in an older book" (5.2.1 p38). Read the book's sidebar before
designing level 2 mixing.

## What the current engine can absorb

### The core is rules-agnostic and small

The generic core has no notion of abilities, classes, or levels. It
provides selections, options, and refs (`template.cljc:37-87`), the
modifier fold with a dependency sort (`entity.cljc:597-621`), plugin
merging (`entity.cljc:627-694`), prerequisites (`entity.cljc:482-494`),
and `ref` pooling (`entity.cljc:423-443`). A modifier is a function from
entity to entity plus the set of attributes it reads. The `make-entity`
and `modifier` macros extract that set from the `?name` symbols in the body
(`entity_spec.cljc:68-102`). The top-level template is a two-key map,
`{::t/base template-base ::t/selections selections}`
(`dnd/e5/template.cljc:1565-1567`). A 2024 template beside it needs no core
change.

The model is also portable. Nothing in it depends on Clojure beyond the
macros that save typing the dependency list. A TypeScript version would
declare each modifier's dependencies explicitly.

The core leaks D&D in three small places: the default `:source :phb`
(`template.cljc:41`), an unused binding that names
`:hit-point-level-increases` (`modifiers.cljc:107`), and comments that
name warlock paths (`entity.cljc:412`, `:574`).

### The 2014 layer is large, and its content is code

The 2014 rules live in `dnd/e5/template_base.cljc` (328 lines), the
constructors in `dnd/e5/options.cljc` (2,699 live lines), the classes in
`dnd/e5/classes.cljc` (2,283 live lines), and the built-in races,
backgrounds, and languages in `spell_subs.cljs:514-928`. Each class is a
map passed to `class-option`. Its structure (`:hit-die`, `:profs`,
`:ability-increase-levels`, `:subclass-level`, `:spellcasting`) is data.
Almost every class feature is a macro call with `?` references, compiled by
`make-entity` and `modifier`. Examples are Second Wind
(`classes.cljc:1068-1074`), Arcane Recovery (`:2404-2408`), and Mystic
Arcanum (`:3043-3055`). A TypeScript consumer cannot author content in this
form without the ClojureScript compiler.

The survey confirmed these 2014 assumptions in the source:

- **Ability increases come from the race.** `race-option` applies
  `:abilities` through `race-ability` (`options.cljc:2268-2271`). The
  accessors `race-ability-increases` and `subrace-ability-increases`
  (`character.cljc:381`, `:384`) and the builder's columns
  (`character_builder.cljs:892-926`) assume it.
- **Backgrounds grant no ability scores or feats.** `background-option`
  (`options.cljc:2453-2506`) has fields for skills, tools, languages,
  equipment, and traits. Built-in content can pass compiled `:selections`
  and `:modifiers` through it. A homebrew background cannot, and plugin
  backgrounds get no `:props` processing (`spell_subs.cljs:90-97`).
- **Feats have no category or level prerequisite.** `feat-prereqs`
  (`options.cljc:3190-3217`) supports ability 13 or higher, spellcasting,
  armor proficiency, and race. Any other prerequisite falls through to
  `armor-prereq`. All feat choices share one `:ref [:feats]` pool.
- **"ASI or feat" is a class-level choice.** `level-option` inserts it at
  each class's `:ability-increase-levels` (`options.cljc:2814-2815`), for
  example `[4 6 8 12 14 16 19]` for the Fighter (`classes.cljc:1055`).
- **Subclass level is data, and it varies.** `:subclass-level` defaults to
  1 (`options.cljc:2784`). Cleric, Sorcerer, and Warlock use 1, Druid and
  Wizard use 2, and the rest use 3. This part fits 2024 already.
- **Spellcasting has three "known" modes.** `:known-mode` is `:schedule`,
  `:all`, or `:acquire`, and the prepared count is the ability modifier
  plus `level / factor` (`template_base.cljc:274-284`). The multiclass
  caster level truncates `level / factor` with `int`
  (`template_base.cljc:263-269`).
- **Fighting styles are a fixed option list** (`options.cljc:1717-1778`).
  Dueling is code that reads `re-frame.db/app-db` (`options.cljc:1746`,
  patch D2).
- **Weapons are open maps.** A `::mastery` key can be added as data, but
  nothing reads it (`weapons.cljc`).
- **Most of the 107 built-character keys are edition-neutral sheet
  values**, such as abilities, AC, hit points, saves, skills, attacks, and
  spell slots. About ten are 2014-shaped: `race-ability-increases`,
  `subrace-ability-increases`, `spell-slot-factors`,
  `total-spellcaster-levels`, `spells-known-modes`, `pact-magic?`,
  `option-sources`, and the Adventurers League fields such as
  `al-illegal-reasons`. List the keys with
  `jq 'keys' fixtures/characters/fighter-1.expected.json`.

The survey also estimated how much of the 2014 layer a 2024 version would
rewrite: about 85% of the live lines in `classes.cljc`, 20% of
`options.cljc`, and 15% of `template_base.cljc`, plus all of the species
and backgrounds. That is a judgment, not a count, but it puts the new
Clojure at several thousand lines.

### Nothing records the edition, and keys are global

- **No edition field.** The strict entity is selections plus a values map
  (`strict.cljc:11-38`). The old server stamps `::se/game :dnd` and
  `::se/game-version :e5` on every save (`routes.clj:861-865`), and the
  Datomic schema has the attributes (`db/schema.clj:205-214`). The original
  authors anticipated versions. But `from-strict` drops the stamp, and the
  check that reads it is commented out (`routes.clj:871`).
- **Keys are global per content type.** `common/name-to-kw`
  (`common.cljc:8-20`) turns "Fireball" into `:fireball` in either
  edition. Plugin content sorts ahead of built-in content in a
  `sorted-set-by` on the key, so a homebrew 2024 Fighter replaces the
  built-in Fighter for every character (`spell_subs.cljs:976-979`). Spells
  behave the same way (`:1185-1193`).
- **Real data already shows the collision.** The owner's export contains
  a pack that re-declares the built-in `hunters-mark`, `counterspell`, and
  `goodberry`. Users who want two versions of one thing today give them
  different names, which yields keys such as `:ranger-revised-`
  (`fixtures/orcbrew/private/all-content3.summary.json`).

### The `.orcbrew` vocabulary is 2014-shaped, and bounded

An `.orcbrew` file is EDN, so it cannot hold functions. Homebrew can
express only what the conversion code interprets: 22 `:props` keys in
`make-feat-modifiers` (`options.cljc:3282-3348`), 12 `level-modifier`
types (`spell_subs.cljs:156-177`), 6 selection keys
(`options.cljc:3256-3271`), and the per-type fields. That vocabulary has
no background ability increases, no background feat, no feat category or
level prerequisite, and no weapon mastery. The homebrew class builder
writes only `:known-mode :schedule` (`views.cljs:5749-5755`).

The bound matters for any rewrite. Porting 2014 homebrew semantics to a
new engine means porting this finite vocabulary, not arbitrary code. The
open-ended part of a 2014 port is the built-in SRD content in
`classes.cljc` and `options.cljc`, and that content is fixed.

## How other projects support both editions

Four open-source projects support both editions. None of them merges the
editions into one key space.

- **Foundry VTT `dnd5e`** runs one engine. A world setting,
  `rulesVersion`, picks the rules, and 31 lines under `module/` branch on
  it. Every document carries `system.source.rules`, `"2014"` or `"2024"`,
  and the 4.0.0 migration marked all older data as `"2014"`. Each edition
  has its own compendium packs. The same concept keeps its `identifier`
  across editions but gets a new document ID.
- **5e-bits** keeps separate `src/2014` and `src/2024` data trees and
  serves them from separate `/api/2014` and `/api/2024` routes. The same
  index, such as `acolyte`, exists in both.
- **Open5e** stores SRD 5.2 as its own document under its own game system,
  and prefixes keys with the document, as in `srd-2024_dwarf`.
- **Charnik**, a TypeScript character builder, binds each character to the
  edition it was created in. Its shared rules functions take a `system`
  argument where the editions differ.

Foundry is the closest match to this project's problem. It needed an
edition tag on every document, separate content sets, and branches in
shared code. Foundry and Charnik both end with one engine that branches on
the edition, not two engines side by side.

## The options

The five options run from least to most change. The comparison table
follows them.

### A. Stay 2014-only and decide later

M1 runs as planned, and 2024 is out of scope until the 2014 app reaches
parity (M7).

- **Cost now.** None.
- **Cost later.** Anything the app builds on 2014 assumptions in the
  meantime must change. If the sheet reads `race-ability-increases`
  directly, stores bare keys, and routes browse pages by bare key, a second
  edition later touches all of that.
- **Verdict.** Option A is fine if 2024 may never happen. The five
  changes under *Changes to make during M1 and M2* cost little and turn
  option A into option E.

### B. 2024 as content on the unchanged engine

Author 2024 species, backgrounds, feats, spells, and classes as `.orcbrew`
packs or as built-in data, with no engine change.

- **What works.** Spells, languages, simple species, and simple feats
  work. So do subclass levels, and prepared casting through
  `:known-mode :all` with hand-edited EDN.
- **What does not work.** The unchanged engine cannot express background
  ability increases and Origin feats in homebrew, feat categories and
  level gates, Weapon Mastery as a mechanic, or the Epic Boon at level 19.
  Anything with a 2014 name needs a renamed key, or it replaces the 2014
  version for every character.
- **Verdict.** It is a stopgap that produces 2024-flavored characters with
  some wrong numbers. It is what users of the old app can do today. It is
  not support.

### C. A second rules edition in ClojureScript on the compiled engine

Add 2024 namespaces beside `dnd/e5/`, for example `dnd/e5_2024/`. They
hold a `template-base`, extended constructors, the classes, species,
backgrounds, feats, and SRD 5.2 spells. The extended constructors cover a
background with ability increases and an Origin feat, feat categories and
level prerequisites, Weapon Mastery, and Epic Boons. `evaluate` takes a
rules edition, and `@dmv/pubdoor` ships both templates, one per chunk.

- **Strengths.** There is one engine, one selection model, and one
  facade. The M4 builder, which renders `evaluate().selections` (ORC-55),
  works for both editions unchanged. Official mixing (level 2) is
  straightforward, because both editions' content is in one engine. A
  2024 template can
  offer 2014 species and backgrounds and apply the conversion rule to them
  in one place. The constructors in `options.cljc` are mostly reusable.
- **Costs.** It needs several thousand lines of new Clojure in the macro
  style above. It reverses the plan's premise that Clojure is only
  mechanical glue (Plan Set 1 doc 03) and the Phase A rule that limits
  engine changes to patches D1 to D4 (`HANDOFF-phase-a.md` §6). The fork
  becomes the permanent home of a growing Clojure codebase. The wrinkles stay: lazy
  attributes with no caching, the re-frame dependency, and bundle size.
- **Compatibility.** C1 to C3 are unaffected for 2014, because the 2014
  namespaces do not change. 2024 keys need a scheme that cannot collide,
  such as a keyword namespace per edition, decided before the first 2024
  character is saved. `.orcbrew` needs new fields for 2024 homebrew, and
  the C1 promise that exports load in the old app holds only for 2014
  packs.
- **Main risk.** One part-time developer maintains a growing codebase in
  a language the rest of the project is leaving.

### D. A new dual-edition engine now, instead of M1

Cancel the compiled-engine M1. Write a new engine in TypeScript that
supports both editions, re-author SRD 5.1 and SRD 5.2 content for it, and
port the `.orcbrew` conversion. Use the M0 fixtures and the old app as the
oracle.

- **Strengths.** The engine is in the app's language and designed for
  editions from the start. No Clojure remains after M0. The wrinkles go
  away, because the new engine can cache attributes and has no re-frame.
- **Costs.** It costs the 6 to 9 months that the decision record avoided,
  plus the 2024 work, all before M2 can render a sheet. The risk register
  already rates solo-developer fatigue as high.
- **Compatibility.** C1 to C3 hold only by testing, not by construction.
  Eleven golden characters and 15 packs are thin evidence for value
  equality across 12 classes and every homebrew field. The oracle can
  generate more cases (see option E), but only after someone builds that
  tooling.
- **Verdict.** This is the "whole new rewrite" in its most direct form.
  It ends where option E ends, at a higher cost and with a later first
  release.

### E. Compiled engine now, new dual-edition engine later (recommended)

Run M1 as planned, with the facade shaped so that a second engine can
implement it. After the 2014 app works, build a new TypeScript engine for
2024. Keep the current core's model: the strict entity as the character
format, a template of selections and options, and modifiers with declared
dependencies. The app picks an engine by the character's edition tag. Port
2014 onto the new engine last, and retire the Clojure build when the port
matches.

- **Strengths.** M1 still delivers working 2014 in weeks. The 2024 engine
  is new design work with no legacy data to match. With the choice-tree
  model kept, the strict entity, the `selections` shape, and the mutations
  mean the same thing in both engines, so the app's builder and storage
  carry over. `@dmv/pubdoor` becomes the oracle for the 2014 port instead
  of a permanent dependency. The port can be tested against it on every
  fixture and on characters that `autofill` (ORC-23) generates at random,
  as many as CI can afford.
- **Costs.** The project maintains two engines during the transition.
  The facade must describe both editions without dropping what only one
  of them has. Level 2 and
  level 3 mixing arrive only when the new engine understands 2014 content.
  The cheapest route is to port the bounded `.orcbrew` vocabulary first.
  That gives 2024 characters access to 2014 homebrew before the full SRD
  5.1 port.
- **Compatibility.** C1 to C3 hold by construction for as long as
  `@dmv/pubdoor` evaluates 2014 characters, and by differential testing
  after the port.
- **Main risk.** The port never happens and two engines persist. That
  outcome is still no worse than option A plus a working 2024 engine.

### Comparison

| | A. 2014 only | B. 2024 as content | C. 2024 in Clojure | D. New engine now | E. New engine later |
|---|---|---|---|---|---|
| Real 2024 rules | No | Partial, some wrong numbers | Yes | Yes | Yes |
| Change to M1 | None | None | None. The patch rule changes later | Replaced | Five small changes |
| Time to a working 2014 app | As planned | As planned | As planned | 6 to 9 months later or more | As planned |
| New Clojure | None | EDN only | Several thousand lines | None | None |
| Identical 2014 sheet values | By construction | By construction | By construction | By testing | By construction, then by testing |
| Official mixing (level 2) | No | No | Straightforward | Designed in | After the 2014 port |
| Engines to maintain | 1 | 1 | 1, larger | 1 | 2 during the transition |
| Clojure at the end | Yes | Yes | Yes, more of it | No | No |

## Changes to make during M1 and M2

None of these changes touches the engine source, the build, or the M1 exit
criteria. Each keeps options C, D, and E open, and each costs hours or a
few days.

1. **Design `evaluate` and `types/index.d.ts` as an edition-neutral
   interface** (ORC-16, ORC-24). Give `evaluate` an explicit rules edition
   argument now, even though only `"2014"` exists. This is Charnik's
   `system` argument. Split the types into a shared sheet (abilities, AC,
   hit points, saves, skills, attacks, spell slots, features) and a 2014
   extension for the 2014-shaped keys listed above. `built` can stay the
   raw accessor dump that the fixtures check.
2. **Have the app render from its own sheet type** (ORC-48). Map
   `evaluate().built` to it in one adapter. A second engine then needs a
   second adapter, not a second sheet.
3. **Tag the rules edition in every document the new app writes**
   (ORC-50, ORC-53). Add `"rules": "2014"` to the `dmv-character` envelope
   and to each stored homebrew pack, as Foundry does with
   `system.source.rules`. A missing tag can default to 2014, because every
   existing file is 2014, so skipping the tag is recoverable. Adding it now
   still costs less than a migration later.
4. **Qualify content keys by edition wherever the app stores or routes
   them** (ORC-49, ORC-87). Browse URLs such as `/spells/fireball` and the
   summaries index collide the day a second `:fireball` exists. Engine keys
   stay as they are (contract C3).
5. **Keep the M0 oracle permanent.** Treat `fixtures/` and `scripts/` as
   the acceptance suite for any future engine, not as M1 set-up. When
   ORC-23 lands, add a script that dumps randomly generated characters and
   their built values, so that a port has a large differential corpus.

Two things not to do now: do not start 2024 content, and do not rename
"race" to "species" or change any key in the 2014 engine. Both break
contract C3 for no present gain.

## Recommendation: keep M1, and run a spike before any 2024 work

Keep M1 and make the five changes. Keep the 2014 app first through M5,
which is the milestone where old-app users can move. Waiting costs nothing,
because the M1 facade and the M2 to M4 app are the same work under C, D,
and E. Committing to a 2024 engine now would delay the only milestones that
move users off the old app.

Before any 2024 work starts, run a spike of about two weeks, and no
earlier than the end of M1, because the spike tests the M1 facade.
Implement the same slice twice: a 2024 Fighter to level 5 with one species,
one background, and its Origin feat.

1. Write the slice in ClojureScript on the compiled core, which takes a few
   days.
2. Port the generic core to TypeScript (about 1,200 lines of Clojure) and
   write the slice on the port. Read Charnik's `src/lib/rules/` first as a
   TypeScript design reference.

The spike shows what a full 2024 ruleset costs each way, and whether the
facade from change 1 can describe a second engine without strain. Choose
between C and E with those numbers.

## Questions to answer before deciding

The owner answered these on 2026-09-23. See *Decision (2026-09-23): option
E* for the answers.

These questions pick the option. The first three matter most.

1. **Must a 2024 character use 2014 content, and from when?** If official
   mixing (level 2) or homebrew mixing (level 3) must work in the first
   2024 release, one engine that holds both editions wins. That is option
   C, or option D. If side by side (level 1) is enough at first, option E
   works. Read the 2024 Player's Handbook sidebar on older species and
   backgrounds before answering, because its wording was not verified.
2. **How long must 2014 characters evaluate to identical sheet values?**
   The plan's definition of done requires it. If that holds forever, any
   replacement for the compiled engine must match it value for value,
   which makes option D, and the port in option E, expensive. If "imports
   and evaluates, with a report of the differences" is acceptable after
   some date, the port gets much cheaper.
3. **Are you willing to own a growing Clojure codebase?** Option C is the
   only path that keeps Clojure long term, and it grows it. Answer for the
   next five years, including who else might contribute.
4. **What content will 2024 users play with?** SRD 5.2 has one subclass
   per class, four backgrounds, and 17 feats. Most real 2024 play uses
   non-SRD books, which users will enter as homebrew. If so, a 2024
   homebrew format and its builder UI matter more than the SRD content.
   The format decision comes early: extend `.orcbrew` with an edition
   field and new fields, or define a new format.
5. **When must 2024 ship relative to the 2014 migration?** After M5 points
   to option E. At the first release points to option C or D.
6. **Is 2014-to-2024 character conversion a feature?** If yes, it is a
   guided rebuild in the builder, and it needs both editions loaded in one
   app. Every option except A and B provides that.
7. **Should a new engine keep the strict entity and choice-tree model?**
   Keeping it is what lets the app, the storage format, and the builder
   carry over between engines. A different model, such as a log of
   level-up events, might suit 2024 better but means a second builder.

These smaller decisions follow from the answers:

- **The key scheme for 2024 content.** A keyword namespace per edition, or
  a prefix as Open5e does (`srd-2024_dwarf`). Decide before the first 2024
  character is saved.
- **Attribution text.** The research note quotes the required statements
  for SRD 5.1 and SRD 5.2.1. SRD 5.2.1 also forbids any other attribution
  to Wizards of the Coast. Shipping both SRDs means showing both
  statements.
- **Reuse of the 5e-bits 2024 data.** `5e-bits/5e-database` has SRD 5.2
  spells, feats, classes, subclasses, species, backgrounds, and mastery
  properties as JSON. Check its license before using it as a starting
  point for content.
- **Monster data in the engine.** The 2024 Wild Shape needs beasts at build
  time, which changes the plan to keep monsters out of the engine chunk.

## Sources

- `docs/kb/srd-5.2-rules-delta.md`: the SRD 5.1 to SRD 5.2.1 rules delta,
  licensing, the backward-compatibility guidance, and how Foundry VTT
  `dnd5e`, 5e-bits, Open5e, and Charnik handle both editions, with
  primary-source citations.
- `docs/ts-rewrite-plan/`: the active plan, in particular
  `02-engine-library.md`, `06-milestones-and-risks.md`, and
  `HANDOFF-phase-a.md`.
- `fixtures/README.md` and `fixtures/orcbrew/private/all-content3.summary.json`.
- Linear: the Plan overview and Risk register documents, project PubDoor
  (M1 is ORC-15 to ORC-26), and project Alchemy 5e (ORC-44 to ORC-92).
