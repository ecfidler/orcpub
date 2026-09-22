# Supporting the 2024 rules: options for the engine library

> Status: exploration report, written 2026-09-22 before M1 started. It is
> not part of the adopted plan (Plan Set 2, `docs/ts-rewrite-plan/`). It
> lays out the options and the questions that decide between them. The
> decision is the owner's.

The question: could a future engine library support both D&D 5e 2014 (SRD
5.1, what orcpub implements) and D&D 5e 2024 (SRD 5.2), and should the
current plan change now, while M1 has not started, to prepare for that?

The rules facts come from `docs/kb/srd-5.2-rules-delta.md`, which cites
the SRD documents and other primary sources. The engine facts come from a
survey of this repository at commit `6248213`. File and line citations are
for that commit.

## Summary

- **A dual-edition engine is viable.** The engine's generic core, about
  1,200 lines in `entity.cljc`, `template.cljc`, `modifiers.cljc`,
  `entity_spec.cljc`, and `entity/strict.cljc`, knows nothing about D&D. A
  second rules edition can sit on the same core, in ClojureScript or in a
  TypeScript port of it.
- **The hard part is 2014, not 2024.** Supporting 2024 is new design work
  with no legacy data to match. Replacing the compiled engine for 2014 is
  the expensive part, because contract C2 requires every imported character
  to evaluate to the same sheet values. The plan's decision record put
  that re-authoring at 6 to 9 months, and a dual-edition rewrite started
  now pays that cost before users see anything.
- **2024 does not fit the current engine as data alone.** Backgrounds that
  grant ability scores and a feat, feat categories with level
  prerequisites, and Weapon Mastery need new engine concepts. Content keys
  also collide: a 2024 `:fighter`, `:elf`, or `:fireball` silently
  replaces the 2014 one (`spell_subs.cljs:976-979`, `:1185-1193`).
- **Recommendation.** Keep M1 as planned. Make five cheap changes during
  M1 and M2 so that a second engine can slot in later (see *Changes to
  make now*). Build 2024 support as a new TypeScript engine after the 2014
  app works, reusing the choice-tree model of the current core. Port 2014
  onto that engine last, using `@dmv/pubdoor` as the test oracle. This is
  option E below. The answer to one question changes the recommendation:
  if a 2024 character must use 2014 content on day one, option C, a second
  rules edition written in ClojureScript on the compiled engine, becomes
  the better path.

## What "support both editions" can mean

The requirement has three levels. Each one costs more than the one above
it, and the options below differ most on the third.

| Level | What the user can do | Example |
|---|---|---|
| 1. Side by side | Each character is 2014 or 2024. Content does not cross | A 2024 Fighter uses only 2024 species, backgrounds, feats, and spells |
| 2. Official mixing | A 2024 character uses 2014 content under the published backward-compatibility rules | A 2024 character takes a 2014 subclass, or a 2014 race whose ability increases are ignored |
| 3. Homebrew mixing | A 2024 character uses the user's existing 2014 `.orcbrew` content | The owner's 29-pack `all-content.orcbrew`, all of it 2014-era, applied to 2024 characters |

Converting a saved 2014 character into a 2024 character is a fourth
possibility. No option below does it automatically, because the two
editions ask for different choices at different levels. It is a guided
rebuild at best.

## What 2024 changes, sorted by engine impact

<!-- FILL FROM docs/kb/srd-5.2-rules-delta.md -->

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

The 2014 assumptions a 2024 edition would hit, each confirmed in the
source:

- **Ability increases come from the race.** `race-option` applies
  `:abilities` through `race-ability` (`options.cljc:2268-2271`). The
  accessors `race-ability-increases` and `subrace-ability-increases`
  (`character.cljc:381`, `:384`) and the builder's columns
  (`character_builder.cljs:892-926`) assume it.
- **Backgrounds grant no ability scores or feats.** `background-option`
  (`options.cljc:2453-2506`) has fields for skills, tools, languages,
  equipment, and traits. Built-in content can pass compiled `:selections`
  and `:modifiers` through it, but a homebrew background cannot, and
  plugin backgrounds get no `:props` processing (`spell_subs.cljs:90-97`).
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

A survey agent estimated that a 2024 version of the same content would
rewrite about 85% of the live lines in `classes.cljc`, 20% of
`options.cljc`, and 15% of `template_base.cljc`, plus all of the species
and backgrounds. That is a judgment, not a count, but it puts the new
Clojure at several thousand lines.

### Nothing records the edition, and keys are global

- **No edition field.** The strict entity is selections plus a values map
  (`strict.cljc:11-38`). The old server stamps `::se/game :dnd` and
  `::se/game-version :e5` on every save (`routes.clj:861-865`), and the
  Datomic schema has the attributes (`db/schema.clj:205-214`), so the
  original authors anticipated versions. But `from-strict` drops the stamp,
  and the check that reads it is commented out (`routes.clj:871`).
- **Keys are global per content type.** `common/name-to-kw`
  (`common.cljc:8-20`) turns "Fireball" into `:fireball` in either
  edition. Plugin content sorts ahead of built-in content in a
  `sorted-set-by` on the key, so a homebrew 2024 Fighter replaces the
  built-in Fighter for every character (`spell_subs.cljs:976-979`). Spells
  behave the same way (`:1185-1193`).
- **This already happens in real data.** The owner's export contains a
  pack that re-declares the built-in `hunters-mark`, `counterspell`, and
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

## The options

Five options, from least to most change. The comparison table follows
them.

### A. Stay 2014-only and decide later

M1 runs as planned, and 2024 is out of scope until the 2014 app reaches
parity (M7).

- **Cost now.** None.
- **Cost later.** Whatever the app encodes about 2014 in the meantime. If
  the sheet reads `race-ability-increases` directly, stores bare keys, and
  routes browse pages by bare key, a second edition later touches all of
  that.
- **Verdict.** Fine if 2024 may never happen. The five changes in *Changes
  to make now* cost little and turn option A into option E.

### B. 2024 as content on the unchanged engine

Author 2024 species, backgrounds, feats, spells, and classes as `.orcbrew`
packs or as built-in data, with no engine change.

- **What works.** Spells, languages, simple species, and simple feats.
  Class subclass levels and prepared casting through `:known-mode :all`
  with hand-edited EDN.
- **What does not work.** Background ability increases and origin feats
  from homebrew, feat categories and level gates, Weapon Mastery as a
  mechanic, and the Epic Boon at level 19. Anything with a 2014 name needs
  a renamed key, or it replaces the 2014 version for every character.
- **Verdict.** A stopgap that produces 2024-flavored characters with
  some wrong numbers. It is what users of the old app can do today. It is
  not support.

### C. A second rules edition in ClojureScript on the compiled engine

Add 2024 namespaces beside `dnd/e5/` (for example `dnd/e5_2024/`): a
`template-base`, extended constructors (a background with ability
increases and an origin feat, feat categories and level prerequisites,
Weapon Mastery, Epic Boons), the classes, species, backgrounds, feats, and
SRD 5.2 spells. `evaluate` takes a rules edition, and `@dmv/pubdoor`
ships both templates, one per chunk.

- **Strengths.** One engine, one selection model, one facade. The M4
  builder, which renders `evaluate().selections` (ORC-55), works for both
  editions unchanged. Official mixing (level 2) is natural, because both
  editions' content lives in one registry and a 2024 template can list
  2014 subclasses. The constructors in `options.cljc` are mostly reusable.
- **Costs.** Several thousand lines of new Clojure in the macro style
  above. It reverses the plan's premise that Clojure is only mechanical
  glue (Plan Set 1 doc 03) and the Phase A rule that limits engine changes
  to patches D1 to D4 (`HANDOFF-phase-a.md` §6). The fork becomes the
  permanent home of a growing Clojure codebase. The wrinkles stay: lazy
  attributes with no caching, the re-frame dependency, and bundle size.
- **Compatibility.** C1 to C3 are unaffected for 2014, because the 2014
  namespaces do not change. 2024 keys need a scheme that cannot collide,
  such as a keyword namespace per edition, decided before the first 2024
  character is saved. `.orcbrew` needs new fields for 2024 homebrew, and
  the C1 promise that exports load in the old app holds only for 2014
  packs.
- **Main risk.** Solo-developer capacity on a language the rest of the
  project is leaving.

### D. A new dual-edition engine now, instead of M1

Cancel the compiled-engine M1. Write a new engine in TypeScript that
supports both editions, re-author SRD 5.1 and SRD 5.2 content for it, and
port the `.orcbrew` conversion. Use the M0 fixtures and the old app as the
oracle.

- **Strengths.** One modern engine in the app's language, designed for
  editions from the start. No Clojure after M0. The wrinkles go away: the
  new engine can cache attributes and has no re-frame.
- **Costs.** The 6 to 9 months the decision record avoided, plus 2024 on
  top, all before M2 can render a sheet. The risk register already rates
  solo-developer fatigue as high.
- **Compatibility.** C1 to C3 hold only by testing, not by construction.
  Eleven golden characters and 15 packs are thin evidence for value
  equality across 12 classes and every homebrew field. The oracle can
  generate more cases (see option E), but only if someone builds that
  first.
- **Verdict.** This is the "whole new rewrite" in its most direct form.
  It reaches the right destination by the most expensive route.

### E. Compiled engine now, new dual-edition engine later (recommended)

Run M1 as planned, with the facade shaped so that a second engine can
implement it. After the 2014 app works, build a new TypeScript engine for
2024. Keep the current core's model: the strict entity as the character
format, a template of selections and options, and modifiers with declared
dependencies. The app picks an engine by the character's edition tag. Port
2014 onto the new engine last, and retire the Clojure build when the port
matches.

- **Strengths.** M1 still delivers working 2014 in weeks. The 2024 engine
  is new design work with no legacy data to match. Keeping the choice-tree
  model means the strict entity, the `selections` shape, and the mutations
  mean the same thing in both engines, so the app's builder and storage
  carry over. `@dmv/pubdoor` becomes the oracle for the 2014 port instead
  of a permanent dependency: the port can be tested against it on every
  fixture and on characters that `autofill` (ORC-23) generates at random,
  as many as CI can afford.
- **Costs.** Two engines during the transition. The facade must describe
  both without becoming a lowest common denominator. Level 2 and level 3
  mixing arrive only when the new engine understands 2014 content. The
  cheapest route to that is porting the bounded `.orcbrew` vocabulary
  first, which gives 2024 characters access to 2014 homebrew before the
  full SRD 5.1 port.
- **Compatibility.** C1 to C3 hold by construction for as long as
  `@dmv/pubdoor` evaluates 2014 characters, and by differential testing
  after the port.
- **Main risk.** The port never happens and two engines persist. That
  outcome is still no worse than option A plus a working 2024 engine.

### Comparison

| | A. 2014 only | B. 2024 as content | C. 2024 in Clojure | D. New engine now | E. New engine later |
|---|---|---|---|---|---|
| Real 2024 rules | No | Partial, some wrong numbers | Yes | Yes | Yes |
| Change to M1 | None | None | None now, new rules later | Replaced | Small, see below |
| Time to a working 2014 app | As planned | As planned | As planned | Plus 6 to 9 months or more | As planned |
| New Clojure | None | EDN only | Several thousand lines | None | None |
| 2014 fidelity (C2) | By construction | By construction | By construction | By testing | By construction, then by testing |
| Official mixing (level 2) | No | No | Natural | Designed in | After the 2014 port |
| Engines to maintain | 1 | 1 | 1, larger | 1 | 2 during the transition |
| Clojure at the end | Yes | Yes | Yes, more of it | No | No |

## Changes to make now

None of these changes the engine source, the build, or the M1 exit
criteria. Each keeps options C, D, and E open, and each costs hours or a
few days.

1. **Design `evaluate` and `types/index.d.ts` as an edition-neutral
   interface** (ORC-16, ORC-24). Give `evaluate` an explicit rules edition
   argument now, even though only `"2014"` exists. Split the types into a
   shared sheet (abilities, AC, hit points, saves, skills, attacks, spell
   slots, features) and a 2014 extension for the 2014-shaped keys listed
   above. `built` can stay the raw accessor dump that the fixtures check.
2. **Have the app render from its own sheet type** (ORC-48). Map
   `evaluate().built` to it in one adapter. A second engine then needs a
   second adapter, not a second sheet.
3. **Tag the rules edition in every document the new app writes**
   (ORC-50, ORC-53). Add `"rules": "2014"` to the `dmv-character` envelope
   and to each stored homebrew pack. A missing tag can default to 2014,
   because every existing file is 2014, so this is not a one-way door. It
   is still cheaper now than a migration later.
4. **Qualify content keys by edition wherever the app stores or routes
   them** (ORC-49, ORC-87). Browse URLs such as `/spells/fireball` and the
   summaries index collide the day a second `:fireball` exists. Engine keys
   stay as they are (contract C3).
5. **Keep the M0 oracle permanent.** Treat `fixtures/` and `scripts/` as
   the acceptance suite for any future engine, not as M1 scaffolding. When
   ORC-23 lands, add a script that dumps randomly generated characters and
   their built values, so that a port has a large differential corpus.

Two things not to do now: do not start 2024 content, and do not rename
"race" to "species" or change any key in the 2014 engine. Both break
contract C3 for no present gain.

## What I would do

Keep M1 and make the five changes. Ship the 2014 app through M5, which is
the point where old-app users can move. Then run a timeboxed spike of one
to two weeks. Port the generic core to TypeScript (about 1,200 lines of
Clojure) and implement a 2024 Fighter to level 5 with one species, one
background, and its origin feat. The spike measures what a full 2024
ruleset costs on a TypeScript core, and it tests whether the facade from
change 1 describes a second engine without strain. Choose between C and E
with that number in hand.

The reason to wait: the M1 facade and the M2 to M4 app are the same work
under C, D, and E. Committing to a 2024 engine before any of it exists buys
nothing and delays the only milestone that moves users off the old app.

## Questions to answer before deciding

These are the questions whose answers pick the option. The first three
matter most.

1. **Must a 2024 character use 2014 content, and from when?** If official
   mixing (level 2) or homebrew mixing (level 3) must work at launch, one
   engine that holds both editions wins, which is option C now, or option D.
   If side by side (level 1) is enough at first, option E works.
2. **How long must 2014 characters evaluate to identical sheet values?**
   The plan's definition of done requires it. If that holds forever, any
   replacement for the compiled engine must match it value for value,
   which is what makes options D and E's port expensive. If "imports and
   evaluates, with a report of the differences" is acceptable after some
   date, the port gets much cheaper.
3. **Are you willing to own a growing Clojure codebase?** Option C is the
   only path that keeps Clojure long term, and it grows it. Answer for the
   next five years, including who else might contribute.
4. **What content will 2024 users actually play with?** SRD 5.2 has one
   subclass per class and few backgrounds and feats. Most real 2024 play
   uses non-SRD books, which users will enter as homebrew. If so, a 2024
   homebrew format and its builder UI matter more than the SRD content, and
   the format decision (extend `.orcbrew` with an edition field, or define
   a new JSON format) comes early.
5. **When must 2024 ship relative to the 2014 migration?** After M5 points
   to option E. At launch points to option C or D.
6. **Is 2014-to-2024 character conversion a feature?** If yes, it is a
   guided rebuild in the builder UI, and it needs both editions loaded in
   one app, which every option except A and B provides.
7. **Should a new engine keep the strict entity and choice-tree model?**
   Keeping it is what lets the app, the storage format, and the builder
   carry over between engines. A different model, such as a level-up event
   log, might suit 2024 better but means a second builder.
8. **What licensing text must the app show?** SRD 5.1 and SRD 5.2 each
   require attribution (see the research note). Confirm the wording and
   where it appears before the first 2024 content ships.

## Sources

- `docs/kb/srd-5.2-rules-delta.md`: the SRD 5.1 to SRD 5.2 rules delta,
  licensing, the backward-compatibility guidance, and prior art in other
  projects, with primary-source citations.
- `docs/ts-rewrite-plan/`: the active plan, in particular `02-engine-library.md`,
  `06-milestones-and-risks.md`, and `HANDOFF-phase-a.md`.
- `fixtures/README.md` and `fixtures/orcbrew/private/all-content3.summary.json`.
- Linear project PubDoor (M1 is ORC-15 to ORC-26, all in Todo on
  2026-09-22) and project Alchemy 5e (ORC-44 to ORC-92).
- Source citations in this report are for commit `6248213`.
