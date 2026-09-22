# 01: The compatibility contract

This document defines three user-level contracts. Each states what the new
app must accept on import, what it must produce on export, and the fixtures
that prove it. Because the rules engine is the same compiled code, the new
app inherits most of the hard parts rather than reimplementing them. This
document is mostly about the cases where the old code does not already do
the right thing.

## C1. Homebrew: `.orcbrew` files in both directions

**Import.** The user exports `all-content.orcbrew` (multi-plugin) or a
single pack, `<pack>.orcbrew` (single-plugin), from the old app and imports
it into the new one. The new app must accept every file the old importer
accepts, including the ten drift forms it cleans automatically:

1. Spurious `nil nil,` pairs.
2. `:disabled? nil`.
3. Empty or nil `:option-pack` and an empty top-level pack name.
4. Trailing commas.
5. Smart quotes, dashes, non-breaking spaces, zero-width characters, and
   other Unicode.
6. Traits and selection options missing `:name`.
7. Entries missing `:name`, `:level`, or `:school`.
8. A single-plugin or multi-plugin top level.
9. `:size` as `"Medium"` or `:medium`.
10. Ability keys as `:con` or `:orcpub.dnd.e5.character/con`.

The old pipeline (`import_validation.cljs:1266-1376`) handles all ten and
is compiled into the library (doc 04). The contract is therefore to call
that pipeline rather than bypass it, and to cover each form with a fixture
so that a library upgrade cannot regress it (`fixtures/orcbrew/drift-01` to
`drift-10`).

M0 corrected two points (`fixtures/README.md` §Findings). First, form 10 is
accepted without effect, not normalized: `{:con 2}` adds nothing to
Constitution, while `race-ability-increases` still reports it (finding 4).
Whether the new importer normalizes it is Linear decision ORC-40. Second,
real exports start with a UTF-8 byte-order mark, so strip `﻿` before
parsing. They also carry internal key conflicts, pack names that disagree
with `:option-pack`, subclasses attached to homebrew or absent classes, and
`:skill-options` without `:choose` (finding 9). The old importer accepts all
of it.

**Mechanics fidelity.** A homebrew race, class, feat, or subclass evaluates
to the same character in both apps. This is inherited: the conversion from
orcbrew records to template options is the same code (`opt5e/race-option`,
`class-option`, `plugin-modifiers`, `level-modifier`, and the rest). Golden
characters that use homebrew content prove it (doc 02 §Golden tests).

**Export.** Per-pack and all-content export produce EDN that the old app's
`::e5/plugins` spec accepts: `:key` is present and equal to the map key,
`:option-pack` is non-empty, and numeric fields contain no `nil`. This is
inherited from the `::e5/export-plugin` and `export-all-plugins` logic if
the facade exposes it (`pr-str` of the plugin map). Feeding the exports to
the old validator in a REPL proves it, as a one-off CI job in this repo.

**Not covered by the format.** Magic items are not an orcbrew content type
in the old app. They are stored server-side, per user. Doc 03 §Magic items
covers how a user brings them across.

Fixtures: `test/duplicate-external-a.orcbrew` and
`test/duplicate-external-b.orcbrew`, the community packs gathered in M0,
and one synthetic file per drift form.

## C2. Characters: from the old app to the new

The old app has no character export. The transfer path (doc 03) is that the
user obtains each character's strict entity from their old instance and
imports the file into the new app. There are two ways to obtain it: the
public URL `https://<old>/dnd/5e/characters/<id>`, which returns Transit
text and needs no login for a shared character, or an exporter bookmarklet
run while logged in.

**Import tolerance.** The table lists every quirk found in real saved
characters and what handles it.

| # | Quirk | Handled by |
|---|---|---|
| R1 | Equipment stored as a map `{item-kw value}` in old data rather than a vector of `{key value}`, under all seven equipment keys | Inherited: `vectorize-equipment` (`character.cljc:274`) |
| R2 | `prepared-spells-by-class` stored as a seq of records | Inherited: `update-values-from-strict` (`:294`) |
| R3 | `slots-used` and `features-used` values as vectors, and a stray `:db/id` in `features-used` | Inherited: the same function |
| R4 | An option `int-value` of `0` or a `string-value` of `""` | Preserved as `0` and `""`. The `or` in `entity.cljc:158` keeps them, because both are truthy in Clojure and ClojureScript. An earlier version of this row said they read back as nil. `fixtures/README.md` finding 3 corrected it. Fixture: `legacy/r4-zero-int-value` |
| R5 | `xps` as a string | Not inherited. The old server coerces it (`routes.clj:930`). The importer must parse it, and a blank or invalid value becomes 0 |
| R6 | `equip/quantity` as a string | Inherited on the save path (`fix-quantities`). Apply it on import too |
| R7 | Unqualified legacy keys such as `:str` and `:quantity` | Not inherited. Detection specs exist (`character.cljc:47-94`), but the migration is disabled with `#_`. Re-enable it in the facade's `importCharacter` (patch D1, doc 02) |
| R8 | Selection keys that do not resolve because homebrew is not loaded | Inherited: `content_reconciliation.cljs` detects them. The UI is the new app's |
| R9 | Duplicate multi-select options with the same key | Inherited: `has-duplicate-selections?`. Tolerate them on read |
| R10 | Transit wire format with namespaced keywords | Decode with `transit-js`, or let the library decode it, which is simpler because `cognitect.transit` is already a ClojureScript dependency of the old client |

**Direction.** One way only. The old app cannot import a character file, so
the new app has no obligation to write characters the old app can read.

Fixtures: the three real Datomic entities in
`test/cljc/orcpub/dnd/e5/character_test.clj:100-113`, the M0 captures of
`GET /dnd/5e/characters/:id` for every golden character, and a synthetic
fixture per quirk from R1 to R9.

## C3. Content identity: the key namespace

Imported characters and homebrew files reference content by keyword key:
`:elf`, `:wizard`, `:acid-arrow`, `:champion`, and selection keys such as
`:martial-archetype`. Because the library is the old engine, keys and
selection structure are identical by construction. The contract is
therefore about not breaking that identity:

- Never re-derive keys in TypeScript. Read them from the engine.
- No patch to the engine source in this fork (doc 02) may touch key
  derivation (`common/name-to-kw`), the 16 explicit spell keys
  (`spells.cljc:82, 271, 300`, and the others), subclass selection keys
  (`name-to-kw subclass-title`), or `ref` paths.
- The proof is a CI test that loads every golden character and every
  fixture `.orcbrew` and asserts zero unresolved option keys.

## Known quirks: which side wins

| Old behavior | New app does | Why |
|---|---|---|
| Multi-plugin import skips per-item validation (`import_validation.cljs:782-794`) | Validate uniformly, in the TypeScript layer around the library call | Leniency comes from the automatic cleaning, not from skipping validation |
| A single invalid entry in localStorage wipes all homebrew on reload (`db.cljs:244-265`) | Not applicable, because storage is the new app's (doc 04). Quarantine invalid entries | Data-loss bug |
| Homebrew rename rewrites only `:class` and `:race` references (`key-reference-map`, `:1382`) | Rewrite all references, including spells' `:spell-lists` and `level-selections` types | Strictly better, and old files are unaffected |
| `:boons` is missing from the import required-fields and content-type names | Add them | Half-supported type |
| The importer ignores a background's `:key` in the file and re-derives it from the name | Honor the key when it equals `name-to-kw(name)`, and warn otherwise | Preserve keys |
| The Forgotten Realms name tables in `character/random.cljc` are non-SRD | Exclude that namespace from the bundle. Provide original name lists or none | Licensing |
| Anything that affects computed values | Match the old app exactly. It is the same code | That is the point of Option 2A |
