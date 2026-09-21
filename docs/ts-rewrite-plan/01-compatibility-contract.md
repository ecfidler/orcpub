# 01 — The Compatibility Contract

Three user-level contracts. Each states what must be accepted on import,
what must be produced on export, and the fixtures that prove it. Because the
rules engine is the same compiled code, most of the hard parts of these
contracts are inherited rather than reimplemented; this document is mostly
about the edges where the old code does *not* already do the right thing.

## C1. Homebrew — `.orcbrew` files, both directions

**Import.** The user exports `all-content.orcbrew` (multi-plugin) or a single
pack (`<pack>.orcbrew`, single-plugin) from the old app and imports it into
the new one. The new app must accept every file the old importer accepts,
including the ten de-facto drift forms it auto-cleans:

1. spurious `nil nil,` pairs; 2. `:disabled? nil`; 3. empty or nil
`:option-pack` and empty top-level pack name; 4. trailing commas;
5. smart quotes / dashes / NBSP / zero-width and other Unicode;
6. traits and selection options missing `:name`; 7. entries missing
`:name` / `:level` / `:school`; 8. single- vs multi-plugin top level;
9. `:size` as `"Medium"` or `:medium`; 10. ability keys as `:con` or
`:orcpub.dnd.e5.character/con`.

The old pipeline (`import_validation.cljs:1266-1376`) handles all ten and is
compiled into the library (doc 04), so the contract here is: **call it, don't
bypass it**, and cover each form with a fixture so a library upgrade can't
regress it (`fixtures/orcbrew/drift-01…10`).

Corrections from M0 (`fixtures/README.md` §Findings): form 10 is *accepted
without effect*, not normalized — `{:con 2}` adds nothing to Constitution
while `race-ability-increases` still reports it (finding 4; whether the new
importer normalizes it is Linear decision ORC-40). Real exports also start
with a UTF-8 byte-order mark (strip `\uFEFF` before parsing) and carry
internal key conflicts, pack names that disagree with `:option-pack`,
subclasses attached to homebrew or absent classes, and `:skill-options`
without `:choose` (finding 9). The old importer accepts all of it.

**Mechanics fidelity.** A homebrew race/class/feat/subclass evaluates to the
same character in both apps. Inherited: the conversion from orcbrew records
to template options is the same code (`opt5e/race-option`, `class-option`,
`plugin-modifiers`, `level-modifier`, …). Proved by golden characters that
use homebrew content (doc 02 §"Golden tests").

**Export.** Per-pack and all-content export produce EDN the old app's
`::e5/plugins` spec accepts: `:key` present and equal to the map key,
non-empty `:option-pack`, no `nil` in numeric fields. Inherited from
`::e5/export-plugin` / `export-all-plugins` logic if the facade exposes it
(`pr-str` of the plugin map). Proved by feeding exports to the old validator
in a REPL (one-off CI job in this repo).

**Not covered by the format**: magic items are not an orcbrew content type
in the old app (they live server-side, per user). Doc 03 §"Magic items"
covers how a user brings those across.

Fixtures: `test/duplicate-external-{a,b}.orcbrew`; community packs
gathered in Phase 0; one synthetic file per drift form.

## C2. Characters — old → new

The old app has no character export. The transfer path (doc 03): the user
obtains each character's **strict entity** from their old instance — via the
public URL `https://<old>/dnd/5e/characters/<id>` (Transit text, no login
needed for a shared character) or via an exporter bookmarklet run while
logged in — and imports the file into the new app.

**Import tolerance.** Every quirk found in real saved characters:

| # | Quirk | Handled by |
|---|---|---|
| R1 | Equipment as a map `{item-kw value}` (old) vs vector of `{key value}` (new), under all seven equipment keys | Inherited: `vectorize-equipment` (`character.cljc:274`) |
| R2 | `prepared-spells-by-class` stored as a seq of records | Inherited: `update-values-from-strict` (`:294`) |
| R3 | `slots-used` / `features-used` values as vectors; stray `:db/id` in `features-used` | Inherited (same function) |
| R4 | Option `int-value 0` / `string-value ""` | Preserved as `0` / `""` — the `or` in `entity.cljc:158` keeps them, both being truthy in Clojure(Script). An earlier version of this row said they read back as nil; `fixtures/README.md` finding 3 corrected it. Fixture `legacy/r4-zero-int-value` |
| R5 | `xps` as a string | **Not inherited** — the old *server* coerces it (`routes.clj:930`). The importer must parse; blank/invalid → 0 |
| R6 | `equip/quantity` as a string | Inherited on save path (`fix-quantities`); apply on import too |
| R7 | Unqualified legacy keys (`:str`, `:quantity`) | **Not inherited** — detection specs exist (`character.cljc:47-94`) but the migration is `#_`-disabled. Re-enable in the facade's `importCharacter` (a listed patch, doc 02) |
| R8 | Selection keys that don't resolve (homebrew not loaded) | Inherited: `content_reconciliation.cljs` detection; UI in the new app |
| R9 | Duplicate multi-select options by key | Inherited: `has-duplicate-selections?` — tolerate on read |
| R10 | Transit wire format with namespaced keywords | Decode with `transit-js` or, simpler, let the library decode it (`cognitect.transit` is already a cljs dependency of the old client) |

**Direction.** One way. The old app cannot import a character file, so the
new app has no obligation to write characters the old app can read.

Fixtures: the three real Datomic entities in
`test/cljc/orcpub/dnd/e5/character_test.clj:100-113`; Phase-0 captures of
`GET /dnd/5e/characters/:id` for every golden character; a synthetic
fixture per R1–R9.

## C3. Content identity — the key namespace

Imported characters and homebrew files reference content by keyword key
(`:elf`, `:wizard`, `:acid-arrow`, `:champion`, selection keys like
`:martial-archetype`). Because the library *is* the old engine, keys and
selection structure are identical by construction. The contract is
therefore about **not breaking it**:

- Never re-derive keys in TypeScript; read them from the engine.
- Any patch to the engine source in this fork (doc 02) must not touch key
  derivation (`common/name-to-kw`), the 16 explicit spell keys
  (`spells.cljc:82, 271, 300, …`), subclass selection keys
  (`name-to-kw subclass-title`), or `ref` paths.
- Proof: a CI test that loads every golden character and every fixture
  `.orcbrew` and asserts zero unresolved option keys.

## Known quirks — which side wins

| Old behavior | New app does | Why |
|---|---|---|
| Multi-plugin import skips per-item validation (`import_validation.cljs:782-794`) | Validate uniformly, in the TS layer around the library call | Leniency comes from auto-clean, not from skipping validation |
| A single invalid entry in localStorage wipes all homebrew on reload (`db.cljs:244-265`) | Not applicable — storage is the new app's (doc 04); quarantine invalid entries | Data-loss bug |
| Homebrew rename only rewrites `:class` / `:race` references (`key-reference-map`, `:1382`) | Rewrite all references (spells' `:spell-lists`, `level-selections` types too) | Strictly better; old files unaffected |
| `:boons` missing from import required-fields / content-type names | Add them | Half-supported type |
| Background `:key` in the file is ignored (re-derived from name) | Honor it when it equals `name-to-kw(name)`, warn otherwise | Preserve keys |
| Forgotten Realms name tables in `character/random.cljc` (non-SRD) | Exclude that namespace from the bundle; provide original name lists or none | Licensing |
| Anything that affects **computed values** | Match the old app exactly (it's the same code) | That's the point of 2A |
