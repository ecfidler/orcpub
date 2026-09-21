# Fixtures — Phase A, M0

Real inputs and oracle-produced expected outputs for the engine package
(`@dmv/pubdoor`, M1) and the homebrew engine path (M3). The plan is
`docs/ts-rewrite-plan/`; the milestone is `HANDOFF-phase-a.md` §4.

The oracle is the **old app's own code** running on the JVM, unmodified:
`entity/build` and the `character.cljc` accessors for characters, and the
`spell_subs.cljs` / `equipment_subs.cljs` subscription chain for the template
(loaded onto the JVM through re-frame's JVM interop — see
`scripts/orcpub/oracle.clj`). Nothing here was typed in by hand except the
raw entities of the golden characters and the synthetic `.orcbrew` packs.

Produced from engine source at commit **`bcd9d68`** (branch `engine`; the
engine source is unchanged from `develop` at that point). Regenerate whenever
`src/cljc` or the two `src/cljs` namespaces above change (see *Regenerating*).

## Layout

```
fixtures/
  characters/
    <name>.strict.json      strict entity, Transit-JSON (verbose mode)
    <name>.expected.json    built values — what evaluate(strict).built must equal
    <name>.selections.json  available selections, flattened with actualPath
    <name>.meta.json        packs needed, description, checks run, round-trip result
  legacy/
    character-test-{1,2,3}.*  the three real Datomic entities from character_test.clj
    r{3..9}-*.*               one synthetic strict entity per import quirk (doc 01 §C2)
  orcbrew/
    <pack>.orcbrew            2 real packs, 1 ported test pack, 10 drift-form packs, 2 community packs
    <pack>.template.json      the old template chain's output for that pack alone
    _srd-baseline.template.json.gz  the SRD-only template shape (gzipped, ~6 MB raw)
  README.md
```

| Set | Count |
|---|---|
| Golden characters | 11 (`characters/`) |
| Legacy entities | 3 real + 8 synthetic (`legacy/`) |
| `.orcbrew` packs | 15, each with a `.template.json`, plus the baseline |

## Formats

### `<name>.strict.json`

The strict entity (`orcpub.entity.strict/entity`) as **Transit-JSON in
verbose mode**, pretty-printed. It is valid JSON and lossless: keywords are
`"~:ns/name"` strings, sets are `{"~#set": [...]}`, integer map keys are
`"~i1"`. Read it with `transit-js` (`transit.reader("json")`) or with the
engine's `importCharacter`; it is byte-for-byte what `GET /dnd/5e/characters/:id`
would return apart from Transit's cache markers, so these files double as
R10 (Transit wire format) fixtures. Selection order is significant and is
preserved as array order.

Code-built characters have no `:db/id`s; the real legacy entities keep
theirs, plus `orcpub.entity.strict/owner` where the original had it.

### `<name>.expected.json`

A single JSON object: every accessor in `character-subs`
(`src/cljs/orcpub/dnd/e5/subs.cljs:628-736`), keyed by the sub's name
(`"skill-profs"`, `"armor-class"`, …), plus `player-name` and `faction-name`,
evaluated on the built character and converted with these rules
(`orcpub.oracle/->plain`; the facade's one-pass extraction must do the same):

- keyword → `"ns/name"`, or `"name"` when unqualified; symbol → its string
- set → array, sorted (by natural order; by `pr-str` if elements are not comparable)
- map → object with keys stringified by the keyword rule (numbers → `"1"`),
  keys sorted; a map with a composite key (e.g. `spells-known`, keyed by
  `[class-name spell-key]`) → `{"__entries": [[key, value], ...]}` sorted by
  the key's `pr-str`
- vector / seq → array in order; ratio → double; `nil` → `null`
- a function anywhere in a value → the string `"#fn"` (none occur in the
  current fixtures); anything else → `pr-str`

The function-valued attributes are evaluated against fixed arguments and
recorded under these keys (the `-fn` suffix of the sub name is dropped):

| Key | Value |
|---|---|
| `armor-class-with-armor` | `[{armor, shield, ac}]` for every combination of carried non-shield armor × carried shield, each side also `null` — exactly `armor-calculations` in `subs.cljs` (items resolved through `::mi5e/all-armor-map`, sorted by key) |
| `weapon-modifiers` | per carried weapon key (normal + magic inventories, resolved through `::mi5e/all-weapons-map`; `null` if unresolved): `attack {standard, finesse}`, `best-attack`, `damage {standard, finesse, off-hand}`, `best-damage`, `best-damage-off-hand`, `dual-wield?`, `has-prof?` |
| `spell-save-dc`, `spell-attack-modifier` | per ability key |
| `class-level` | per class key in `levels` |
| `tool-bonus` | per tool key in `tool-profs` |
| `prepare-spell-count` | per class name in `prepares-spells` |

Three JVM-only adjustments were needed to evaluate what the browser
computes; all are documented in *Findings* and none changes a value:
non-magical armor gets `::mi5e/magical-ac-bonus 0` before the AC function
runs, the `?damage-bonus-fns` entries are wrapped for JavaScript's lenient
arity, and `proficiency-help` tolerates a nil count
(`orcpub.oracle/install-js-semantics!`).

### `<name>.selections.json`

`entity/available-selections` for the built character, flattened: `key`,
`name`, `path`, `actualPath` (the `ref` path when the selection has one —
doc 02 wrinkle 5), `min`, `max`, `remaining` (`entity/count-remaining`),
`optionCount`, `selected` (option keys currently chosen), and `ref`,
`multiselect`, `sequential`, `requireValue` when set. Option lists are not
included. Note that a `ref` selection appears once per occurrence in the
tree (e.g. `[languages]` from the race with `min 1` and from the background
with `min 2`); the old UI merges those.

### `<name>.meta.json`

`orcbrew` (packs the character needs, in import order, relative to
`fixtures/orcbrew/`), `description`, `strictRoundTrip` (whether
`to-strict(from-strict(x)) = x`, or the exception it throws), `unfilledSelections`
(required selections the UI would still flag), `checks` (hand-written
assertions the generator ran, with expected/actual/pass), and `quirk` for
the legacy set.

### `<pack>.template.json`

What the old subscription chain produces for that pack loaded alone,
after the old importer (`import_validation.cljs`, progressive strategy,
auto-clean on — the `::e5/import-plugin` event path):

- `pack` — the source name the old UI would use (file name without `.orcbrew`)
- `import` — the importer's result: `success`, `changes` (every auto-clean
  operation), `skipped-items`, `key-conflicts`, `imported-count`, and the
  parse error fields when parsing failed
- `plugins` — app-db `:plugins` after the import: source name → content type
  → sorted item keys
- `content` — the chain's intermediate subscriptions (`::races5e/races`,
  `::bg5e/backgrounds`, `::classes5e/classes`, `::feats5e/feats`,
  `::langs5e/languages`, invocations, boons, and the `plugin-*` lists), as
  key/name lists
- `templateSummary` — per top-level selection: `min`, `max`, `optionKeys` in
  template order
- `templateDelta` — the structural difference between this pack's template
  shape and the SRD-only shape (below)

The **template shape** (`orcpub.oracle/selection-shape`) of a selection is
`key`, `name`, `min`, `max`, `options`, and `ref`, `order`, `tags`,
`multiselect`, `sequential`, `requireValue` when set; of an option it is
`key`, `name`, `order`, `prereqs` (count), `selections` (recursive) and
`modifiers` — each modifier's `key`, and `name`/`value` when they are plain
data. No functions.

The **delta** matches nodes by `key` (selection nodes have `options`
children, option nodes have `selections` children) and keeps only what
differs: a node is present with its key, the scalar fields that changed, the
child diffs, `removed-options` / `removed-selections` (keys present only in
the baseline) and `removed-fields`; a child absent from the baseline is
included whole. So a pack that adds a subclass shows up as
`class → <class> → levels → level-N → <subclass selection> → <new option>`.
`_srd-baseline.template.json.gz` holds `{"summary", "shape"}` for the SRD
template so the baseline itself can be checked (it is the C3 content
identity baseline: every built-in selection and option key).

M3 reproduces this by computing the same shape from `buildTemplate({})` and
`buildTemplate(pack)` and diffing the same way; the two functions are ~60
lines in `scripts/orcpub/oracle.clj`.

## Golden characters

All SRD except where a pack is listed. Ability scores, hit points (average
per level) and every required selection are filled the way the old builder
would; `unfilledSelections` is empty for all eleven.

| Name | What it covers | Packs |
|---|---|---|
| `fighter-1` | Human (standard) fighter 1, Acolyte, Defense, chain mail + longsword + shield | — |
| `fighter-5` | Hill dwarf fighter 5, Champion, **Dueling** (+2 damage on the one-handed longsword: the `patch D2` case), ASI, Extra Attack, tool proficiency | — |
| `fighter-11` | Half-orc fighter 11, Champion, Great Weapon Fighting, three attacks, three ASIs, greataxe | — |
| `fighter-20` | Human fighter 20, Champion, Protection, four attacks, every ASI (one as the Grappler feat), plate + shield, a `+1` longsword and an attuned amulet of health | — |
| `wizard-1` | High elf wizard 1, racial cantrip, 6 spells known, 4 prepared | — |
| `wizard-5` | Rock gnome wizard 5, Evocation, ASI, 3rd-level slots, 14 spells known | — |
| `wizard-11` | Tiefling wizard 11, Evocation, two ASIs, 6th-level slots, 5 wizard cantrips + Thaumaturgy, cloak of protection | — |
| `wizard-20` | Human wizard 20, Evocation, every ASI, 9th-level slots, 44 spells known, Spell Mastery and Signature Spells chosen | — |
| `fighter-3-wizard-2` | Half-elf multiclass; wizard as the second class (no wizard skill pick, hit points at wizard level 1), combined-level spell slots | — |
| `warlock-10-drow` | The `warlock_test.clj` entity verbatim: Drow warlock 10 of the Archfey, Spy, Keen Mind, Pact of the Tome, five invocations. Its nine assertions are re-run as `checks` | `warlock-test-content.orcbrew` |
| `ironwrought-artificer-3` | Homebrew-only race/subrace/class/subclass | `duplicate-external-b.orcbrew` |

Spell picks are deterministic: 6 first-level spells at level 1, then two
spells of the highest castable level per level, walking the SRD wizard list
in order (`wizard-spells-known` in `scripts/golden-characters.clj`).

## Legacy fixtures

| Name | Quirk | Content |
|---|---|---|
| `character-test-1..3` | real data | The three Datomic entities from `test/cljc/orcpub/dnd/e5/character_test.clj` (`strict-round-trip`, `-2`, `-3`), extracted by the reader, unmodified. `-2` has non-SRD content that does not resolve (Eldritch Knight, Noble, Ritual Caster) — real R8 data. All three are incomplete characters; `unfilledSelections` lists what the UI would flag |
| `r3-slots-used-vectors` | R3 | `slots-used` as vectors, stray `:db/id` in `features-used` |
| `r4-zero-int-value` | R4 | hit-point roll `int-value 0`, name `string-value ""` — see *Findings*: both are preserved, not nil |
| `r5-xps-string`, `r5-xps-blank-string` | R5 | `xps` as `"6500"` and as `" "`; expected values use the parsed int (6500 / 0) |
| `r6-quantity-string` | R6 | a weapon quantity `"2"` |
| `r7-unqualified-keys` | R7 | `:str`-style ability keys, `:quantity`/`:equipped?` equipment values, `:character-name`/`:xps`/`:custom-equipment` values; expected values are for the **migrated** entity (see `orcpub.oracle/legacy-normalize`, the behaviour `importCharacter` must implement for patch D1) |
| `r8-unresolved-keys` | R8 | `ironwrought-artificer-3` without its pack: race, subrace, class, subclass all unresolved |
| `r9-duplicate-options` | R9 | `magic-missile` twice in the wizard's spells known |

R1 (equipment as a map `{item-kw value}`) is a *raw-entity* shape: a strict
entity always carries `options` vectors, so it cannot be expressed as a
`.strict.json`. R2 (`prepared-spells-by-class` as records) is the normal
strict shape and every wizard fixture carries it.

For R5 and R7 the expected values are produced after `legacy-normalize`,
which is the intended new-app behaviour rather than inherited engine
behaviour; every other fixture is built exactly as `char5e/from-strict` +
`entity/build` leave it.

## Homebrew packs

| File | Source | License | Exercises |
|---|---|---|---|
| `duplicate-external-a.orcbrew`, `-b` | this repo, `test/` (copied as-is) | EPL-2.0 (repo) | classes/subclasses/races/subraces; `-b` also has drift form 10 (`:abilities {:con 2}`) and the `custom-lineage` key overlap with `-a` for conflict detection |
| `warlock-test-content.orcbrew` | ported from `test/cljc/orcpub/dnd/e5/warlock_test.clj` (Spy, Keen Mind, Drow) | EPL-2.0 (repo) | plugin background, feat with `:ability-increases`, subrace with `:props` weapon proficiencies attached to a built-in race |
| `drift-01-nil-nil-pairs` | synthetic (this repo) | EPL-2.0 | `nil nil,` pairs in items and traits |
| `drift-02-disabled-nil` | synthetic | EPL-2.0 | `:disabled? nil` |
| `drift-03-empty-pack-names` | synthetic | EPL-2.0 | multi-plugin file whose pack is named `""`, items with `:option-pack ""` and `nil` |
| `drift-04-trailing-commas` | synthetic | EPL-2.0 | trailing commas in maps and vectors; a homebrew language |
| `drift-05-unicode` | synthetic | EPL-2.0 | smart quotes, en/em dashes, NBSP, zero-width space, ellipsis, ©/™ |
| `drift-06-missing-names` | synthetic | EPL-2.0 | a trait and a selection option without `:name`; duplicate selection options |
| `drift-07-missing-fields` | synthetic | EPL-2.0 | a spell without `:name`/`:level`/`:school`, a class without `:name` |
| `drift-08-multi-plugin` | synthetic | EPL-2.0 | two named packs in one file; a subrace in pack two extending a race in pack one |
| `drift-09-size-forms` | synthetic | EPL-2.0 | `:size "Medium"`, `:size :medium`, a subrace with `:size "Small"` |
| `drift-10-ability-key-forms` | synthetic | EPL-2.0 | `:abilities {:con 2}` vs `{:orcpub.dnd.e5.character/con 2}`; feats with `#{:con}` vs namespaced |
| `community-mezzoloth-race.orcbrew` | the repo owner's own homebrew, taken verbatim (pack `"me"`) from their old-app `all-content` export | EPL-2.0 (author's own work, contributed here) | a race authored in the old UI: racial spells with `:value`/`:level`, `:languages` as a set, pack-level `:disabled? false` |
| `community-dandwiki-star-elf.orcbrew` | D&D Wiki (dandwiki.com), as recorded in the pack name; transcribed into the old app by the repo owner | GNU FDL 1.3 (D&D Wiki's license) — **verify the page before relying on it** | a subrace attached to the built-in Elf with `:props` weapon/skill proficiencies and level-gated racial spells |

**Community packs: two, not three.** The handoff asks for ≥3 packs published
openly by their authors. No code-search route was reachable from the
sandbox this was produced in (GitHub API and HTML, Sourcegraph, grep.app,
archive.org; the one GitLab project found has no readable repository). The
repo owner's own `all-content.orcbrew` export (29 packs, 1412 items) was
then reviewed: everything else in it is WotC book text transcribed by
community members (PHB, XGtE, TCoE, MToF, VGtM, GGtR, SCAG, EE, DMG, MPMM,
Eberron, UA) or Critical Role's Blood Hunter and Gunslinger (free to
download, not redistributable), so it cannot be committed. The export itself
was run through the oracle in full; see *Findings* 9. Add packs by dropping
the `.orcbrew` into `fixtures/orcbrew/`, recording source and license in the
table above, and running the template dump (below).

## Regenerating

From the project root, with Leiningen (the supported path):

```sh
lein run -m clojure.main scripts/golden-characters.clj          # characters/ and legacy/
lein run -m clojure.main scripts/dump-template.clj --baseline fixtures/orcbrew/_srd-baseline.template.json.gz
lein run -m clojure.main scripts/dump-template.clj fixtures/orcbrew/<pack>.orcbrew fixtures/orcbrew/<pack>.template.json
lein run -m clojure.main scripts/dump-built-character.clj <in.strict.json> <out.expected.json> \
     [--selections <out.selections.json>] [--orcbrew <pack.orcbrew> ...]
```

Or in `lein repl`: `(load-file "scripts/golden-characters.clj")` regenerates
everything; `(load-file "scripts/dump-template.clj")` and
`(load-file "scripts/dump-built-character.clj")` define `dump-template` and
`dump-built-character`.

Without Leiningen or Clojars access, `scripts/oracle-env.sh` builds an
equivalent classpath from Maven Central jars and pinned GitHub checkouts of
the three pure-Clojure libraries the engine needs on the JVM (re-frame,
macrovich, bidi) and runs the same scripts:

```sh
scripts/oracle-env.sh scripts/golden-characters.clj
scripts/oracle-env.sh scripts/dump-template.clj ...
scripts/oracle-env.sh test      # the engine test namespaces (40 tests)
```

Generation is deterministic; a regeneration on unchanged engine source must
produce no diff. `dump-built-character.clj` on a checked-in `.strict.json`
reproduces its `.expected.json` and `.selections.json` byte for byte.

## Not done in M0

- **UI spot check** (HANDOFF §4.2: save a character in the old UI, fetch it
  with `GET /dnd/5e/characters/<id>`, compare with the code-built entity).
  No Datomic transactor or browser was available. The code-built entities
  follow the shapes of the three real entities (starting-equipment flags,
  average hit points with a stored value, ref selections at their global
  path), but they have not been compared against a UI-saved character.
- **Community `.orcbrew` packs** (see above).
- `lein test` was not run here (Clojars is unreachable); the same five engine
  test namespaces pass on the fallback classpath, and no engine source was
  modified.

## Findings — where the plan or the engine differs from what was assumed

Line numbers are for commit `bcd9d68`.

1. **`armor-class-with-armor` needs a JS null-as-zero.**
   `template_base.cljc:76` adds `(::mi5e/magical-ac-bonus armor)`, which is
   `nil` for every non-magical armor. In the browser `(+ x nil)` is `x`; on
   the JVM it throws. `lein test` never calls the AC function with plain
   armor. The oracle defaults the field to 0. The compiled engine is
   unaffected (it runs in JS), but a JVM-side golden test would be.
2. **The Dueling fighting style relies on JS arity and on the off-hand slot.**
   `options.cljc:1739` pushes `(fn [weapon _] ...)` onto `?damage-bonus-fns`,
   which `template_base.cljc:240` calls with one argument (JVM: ArityException;
   JS: pads). Its condition (`:1743-1750`) is true only when
   `::char5e/main-hand-weapon` is a one-handed melee weapon **and**
   `::char5e/off-hand-weapon` is set to something that is not a weapon
   (`:shield`) — with an empty off hand the bonus does not apply. Both belong
   in patch D2 alongside the `app-db` read (`:1746`), and the facade docs
   should say how `evaluate` expects the hand slots to be filled.
3. **Doc 01 R4 is wrong.** `entity.cljc:158` `(or int-value map-value string-value)`
   keeps `0` and `""` (truthy in Clojure and ClojureScript). A hit-point roll
   of 0 counts as 0 and an empty name stays `""`; nothing reads back as nil.
   Fixture: `legacy/r4-zero-int-value`.
4. **Drift form 10 is accepted, not normalized.** The old importer does not
   rewrite `:con`-style ability keys; `race-option` passes them to
   `modifiers/race-ability` verbatim, so `{:con 2}` adds nothing to
   Constitution (`?abilities` sums only the namespaced keys) while
   `race-ability-increases` reports `{"con": 2}`. Feats intersect
   `:ability-increases` with the namespaced keys, so `#{:con}` adds nothing
   either. Fixtures: `ironwrought-artificer-3` (CON stays 15),
   `drift-10-ability-key-forms`. Doc 01 §C1 should list this as "accepted
   without effect", and the new importer may want to normalize it (a
   behaviour change to decide deliberately).
5. **re-frame handlers rely on JS arity.** Many subscription handlers in
   `spell_subs.cljs` / `equipment_subs.cljs` declare one parameter and are
   called with two (`re-frame` passes `input-values query-vec`). Irrelevant in
   the browser; the oracle rewrites single-arity handler literals to variadic
   when loading them on the JVM. M3's de-re-framed functions can simply take
   one argument.
6. **The `duplicate-external-*` subclasses use a `:level-modifiers` shape the
   engine does not understand** (`[{:level 3 :modifiers []}]` instead of
   `{:type … :value …}`); `spell_subs.cljs:156-176` logs "Unknown
   level-modifier type" and drops it. The packs were written for duplicate-key
   detection only; their `.template.json` reflects the dropped modifier.
7. **Saving an unmigrated R7 character.** On the JVM `char5e/to-strict` of an
   entity with `:quantity`-style keys throws in `fix-quantity`
   (`character.cljc:176`, `(int nil)`); in the browser `(int nil)` is 0, so the
   old app silently zeroes such quantities on save. The importer must migrate
   before anything reaches `to-strict`. Likewise `to-strict` drops a string
   `xps` (`remove-nans`, `:244`), so R5 must be parsed on import.
8. **Environment.** Clojars (and download.clojure.org) were unreachable from
   the sandbox; `scripts/oracle-env.sh` exists for that case and is not needed
   where Leiningen works. shadow-cljs itself is installable from npm
   (`shadow-cljs-jar` bundles the JVM side), so M1 does not depend on Clojars
   if the ClojureScript libraries are supplied as source paths.
9. **What a real `all-content.orcbrew` export looks like** (the repo owner's,
   29 packs, 1412 items, 2.2 MB; not committed). The old importer accepts all
   of it (0 skipped, 0.4 s on the JVM) and the template chain builds 52
   races, 15 classes, 63 backgrounds, 115 feats in 1.9 s. Things the plan's
   drift list does not mention, all of which the new importer will meet:
   - The file starts with a UTF-8 **byte-order mark**. Browsers strip it in
     `FileReader.readAsText`, so the old app never sees it; fed to the old
     importer directly it does not report a parse error but throws in
     `fill-missing-in-plugin` (the reader returns a symbol). Strip `\uFEFF`
     before parsing.
   - Pack keys and `:option-pack` values disagree freely: one pack carries
     items from several `:option-pack` names, including Discord handles
     ("Players Hand Book (arandomstringofnum#2919)"); the same book appears
     as two packs ("Tasha's Cauldron of Everything" and
     "Tashas_Cauldron_of_Everything", the latter twice, one copy disabled).
     Hence **86 internal key conflicts** in one file, which the old UI would
     route through the conflict modal; the oracle imports as if resolved.
   - `:disabled?` occurs as `nil`, `true` and `false` at pack and item level.
   - Subclasses attached to *homebrew* classes (`:class :ranger-revised-`,
     `:blood-hunter`, `:blood-hunter-order-of-the-profane-soul-`) and to a
     class that is not present at all (Tasha's `:armorer` → `:artificer`).
     Warlock patrons stored under the Blood Hunter pack with the Blood
     Hunter's key as `:class`.
   - Four subclasses have `:profs {:skill-options {:options {...}}}` with no
     `:choose`. `options.cljc:848` `(> nil 1)` throws on the JVM (JS: false),
     and only when the level options are realized — the subscription chain
     itself never realizes them, so this surfaces the first time a template
     shape or a character at that class is built. Shimmed in the oracle.
   - 206 keys end in `-` (parenthesised names through `name-to-kw`);
     `:prereqs #{}` and `:languages` as sets; `:equipment-choices ()` as an
     empty list; `?` in place of apostrophes (mojibake from a copy/paste);
     nil monster saving throws and a nil `:spell-list-kw` (both handled by
     the old cleaner); `:cleric-spells` as nested level→index→spell maps.
   - The proper `:level-modifiers` shape is `[{:type … :value … :level …}]`,
     confirming that the nested form in `duplicate-external-*` (finding 6)
     is an artefact of those test files, not of real exports.
   - The full template delta against SRD is 9.8 MB of JSON; a keys-only
     summary (import log, plugin keys, content lists, top-level option keys)
     is ~150 KB and would make a useful private regression target for M3/M5
     if the owner wants to keep the export alongside the repo.
