# 06 — Homebrew and `.orcbrew` Compatibility

Everything needed to import every `.orcbrew` file the old app accepts,
export files the old app accepts, and compute homebrew content identically.
Source of truth: the homebrew investigation report (`import_validation.cljs`,
`spell_subs.cljs`, `options.cljc`, `docs/ORCBREW_FILE_VALIDATION.md`,
`docs/HOMEBREW_REQUIRED_FIELDS.md`).

## The file format

- Plain-text **EDN**. No header, no version field, no migration code in the
  old app — compatibility is by lenient auto-clean only.
- Two top-level shapes, discriminated by key type:
  - **multi-plugin** `{"Pack Name" {<content-type> {<key> <entry>}}}` — what
    "Export All" writes and what localStorage holds;
  - **single-plugin** `{<content-type> {<key> <entry>}}` — per-pack export;
    both test fixtures use it. Filed under the filename (minus extension) on
    import.
- Content-type keys: `:orcpub.dnd.e5/classes`, `/subclasses`, `/races`,
  `/subraces`, `/backgrounds`, `/feats`, `/spells`, `/monsters`,
  `/encounters`, `/languages`, `/invocations`, `/boons`, `/selections`
  (13). `:disabled? true` may appear beside them (whole pack off) or inside
  any entry.
- Entry: `{:key :kw :name "…" :option-pack "Pack" …}`. The **only**
  spec-required field on import is `:option-pack`. `:key` and the map key can
  disagree; for classes/subclasses the map key wins, for others the entry's
  `:key` is used — normalize both to the map key on read, write both.

## EDN in TypeScript

Need a reader and a printer:

- **Reader**: parse EDN to plain JS. Requirements: keywords (`:a/b`),
  symbols, strings with Clojure escapes, ints, floats, ratios (`1/2` — old
  monster CR), booleans, nil, vectors, lists, maps, sets `#{}`, and
  `#_` discard. No tagged literals needed. Existing libs: `edn-data`,
  `jsedn` — evaluate; a hand-written parser is ~300 lines and worth owning
  for error messages with line numbers (the old importer scrapes them from
  `cljs.reader` errors).
- **Printer**: must emit what `pr-str` emits so the old app's
  `cljs.reader/read-string` accepts it: keywords as `:ns/name`, sets as
  `#{…}`, strings with `\"`/`\\`/`\n` escapes, and — important — no
  trailing commas, no `nil nil` pairs. Round-trip test: for every fixture,
  `read(print(read(f)))` ≡ `read(f)`.
- **Pretty printer** for the "pretty export" option (optional).

## Lenient import — the auto-clean pipeline

Reproduce the old pipeline in the same order (`import_validation.cljs:1266-1376`):

1. **Text-level fixes before parsing**: `disabled?\s+nil` → `disabled? false`;
   remove `nil\s+nil\s*,\s*` pairs; strip trailing commas before `}`/`]`.
2. **Parse** with line-numbered errors and hints for unmatched delimiters /
   EOF / invalid tokens.
3. **Unicode normalization** of every string (smart quotes, dashes, NBSP and
   other spaces, zero-width, ellipsis, bullets, ×, ÷, ®, ©, ™ — the table at
   `import_validation.cljs:23-68`).
4. **Data cleaning**: empty top-level pack name → `"Unnamed Content"` (with
   numeric suffixes); `:option-pack ""`/`nil` → `"Unnamed Content"`; remove
   entries whose key is `nil`; `nil` → `false` for `:disabled?`; **preserve**
   `nil` for `:spell-list-kw`/`:ability`/`:class-key`; **remove** `nil` for the
   numeric/optional set (`:str :dex … :ac :hp :speed :level :modifier :die
   :die-count :spellcasting`).
5. **Fill missing required fields** with placeholders (`"[Missing Name]"`,
   `"[Missing Spell Name]"`, level 0, school `"unknown"`, nested trait and
   selection-option names).
6. **Dedup selection options** by derived key: identical → drop, different →
   rename `"Name 2"`, `"Name 3"`.
7. **Duplicate-key detection** (internal: same key in two packs of the file;
   external: key collides with a loaded pack from a *different* source).
8. **Validate** every entry (single- and multi-plugin alike — fixing the old
   asymmetry) against the JSON schema of doc 02's orcbrew-shaped records;
   keep valid entries, report skipped ones with reasons (progressive
   import).

Ten forms of drift observed in the field and covered by the above: spurious
`nil nil`, `:disabled? nil`, empty option-pack / pack name, trailing commas,
smart quotes, missing trait/option names, missing entry names/levels/schools,
single vs multi top-level, `:size` as `"Medium"` or `:medium`, ability keys
as `:con` or `:orcpub.dnd.e5.character/con`. One synthetic fixture each.

## Mapping orcbrew records to the content schema

Doc 02's records are designed so this is a rename, not a rewrite. The
mechanics vocabularies and where the old app converts them:

| orcbrew field | Old converter | Schema target |
|---|---|---|
| `:traits [{:name :description :level :type :page :summary}]` | `opt5e/traits-modifiers` → `trait-cfg` | `trait` effects, level-gated |
| `:props {…}` (races, subraces, feats, monsters) | `opt5e/plugin-modifiers` → `make-feat-modifiers` (`options.cljc:3282`) + `make-feat-selections` (`:3256`) | one effect or selection per prop key; the full key list (scalar: `initiative`, `max-hp-bonus`, `speed`, `flying-speed`, `swimming-speed`; boolean: `two-weapon-ac-1`, `passive-perception-5`, `medium-armor-max-dex-3`, `lizardfolk-ac`, …; map: `language`, `skill-prof`, `armor-prof`, `weapon-prof`, `damage-resistance`, `damage-immunity`, `saving-throw-advantage`, `tool-prof-or-expertise`, `skill-prof-or-expertise`; choices: `weapon-prof-choice`, `language-choice`, `skill-tool-choice`, `ritual-casting`, `magic-novice`, `attack-spell`) is the required set |
| `:level-modifiers [{:type :value :level}]` | `spell-subs/level-modifier` (`spell_subs.cljs:156`) — 12 types | `levels[n].effects` |
| `:level-selections [{:type :level :num}]` | `level-selection` (`spell_subs.cljs:334`) — `:type` is a `selections` entry key | `levels[n].selections` with `options.from = selection` |
| `:spellcasting {…}` | `class-option` (`options.cljc:2892-2977`) | verbatim |
| `:spells [{:level :value {:key :ability :level}}]` (races) | `spell-subs/spell-modifiers` | `spells-known` effects |
| `:abilities {:con 2}` / namespaced | `race-ability` / `subrace-ability` | `ability` effects (normalize key form) |
| `:profs {...}` | per-builder | `profs` record |
| `:paladin-spells` / `:cleric-spells` / `:warlock-spells` `{lvl {idx key}}` on subclasses | `make-levels` | subclass `domainSpells` |
| `:subclass-title` | selection key = `name-to-kw(title)` | same rule — contract C2 |
| `:class` (subclass) / `:race` (subrace) | `group-by` attachment | `parent` field; the only attachment mechanism |
| `:spell-lists {:wizard true}` (spells) | `plugin-spell-lists` inversion | registry adds the spell to each class list |

Behavior details to preserve: subrace `:speed`/`:darkvision` are emitted as
*deltas* vs the race, only when different (`options.cljc:1981`); plugin
classes skip the built-in ASI and HP selections when `:plugin?` is set
(`level-option`) — check whether that's intentional or a gap, and decide;
class names display as `"Name (Source)"` when the source isn't the default
pack.

"Lossless import" test: for each fixture and community pack, import →
export → import yields the same registry, **and** a character using that
content evaluates to the same sheet values in both apps (golden characters
with homebrew, Plan Set 1 doc 01 §0.3).

## Export

- Per-pack: single-plugin EDN, `<pack>.orcbrew`. All: multi-plugin,
  `all-content.orcbrew`. Canonical form per contract C3.
- Pre-export validation: required fields (with the old "Export Anyway"
  placeholder-fill option), then full schema validation; block on failure.
- Must pass the old app's `::e5/plugins` spec — test by feeding exports to
  the old validator in a Clojure REPL (one-off CI job in this repo).

## Storage, loading, conflicts

- Store homebrew in IndexedDB (not a single localStorage string). Quarantine
  invalid entries instead of discarding everything (fixes `db.cljs:244-265`).
- Support `LOAD_HOMEBREW_URL`-style server-seeded content: fetch a
  multi-plugin file on first load when storage is empty (`index.clj:163-180`
  behavior) — but run it through the import pipeline rather than storing
  raw.
- Conflict resolution UI per `docs/CONFLICT_RESOLUTION.md`: per conflict
  rename-import (suggested key = `key-slug(source)`, e.g.
  `:artificer-kibbles-tasty`), skip, or replace; "rename all". On rename,
  rewrite **all** references (subclass `:class`, subrace `:race`, spell
  `:spell-lists`, selection references in `:level-selections`) — broader
  than the old `key-reference-map`.
- Missing-content reconciliation for loaded characters
  (`content_reconciliation.cljs`): detect option keys that don't resolve,
  suggest similar keys (exact / prefix / base / display-name scoring, or
  Levenshtein), let the user remap. Don't hardcode the SRD exclusion list —
  derive it from the registry's `source === "srd"`.

## Known old bugs — fix, don't replicate

- `:boons` missing from required-fields and content-type-names.
- Multi-plugin imports skip per-item validation.
- Rename only rewrites `:class`/`:race`.
- One bad entry wipes all homebrew on reload.
- Background `:key` in the file is ignored (key re-derived from name) —
  honor the file's key but verify it equals `name-to-kw(name)`, warn if not.

## Deliverables

- [ ] `packages/edn` reader/printer with round-trip tests
- [ ] Import pipeline with one fixture per drift form; progressive + strict modes
- [ ] orcbrew ↔ schema mapping with lossless tests on fixtures and community packs
- [ ] Export validated against the old spec
- [ ] Conflict resolution and missing-content reconciliation
