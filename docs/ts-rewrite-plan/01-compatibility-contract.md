# 01 — The Compatibility Contract

This document is the specification the rest of the plan implements. Each
section defines one contract: the data that must round-trip, the tolerance
required on read, the canonical form required on write, and the fixture set
that proves it.

Source-of-truth references are into this repository (the old app).

## C1. Characters — the strict entity format

### The format

Characters are stored and exchanged as a **strict entity**
(`src/cljc/orcpub/entity/strict.cljc`): a tree of selections and options plus
a values map. Shape, with `::se` = `orcpub.entity.strict`:

```clojure
{:db/id          17592186045432                 ; present on saved characters
 ::se/owner      "username"
 ::se/type       :character  ::se/game :dnd  ::se/game-version :e5
 ::se/selections [{::se/key :race
                   ::se/option {::se/key :elf
                                ::se/selections [{::se/key :subrace
                                                  ::se/option {::se/key :high-elf}}]}}
                  {::se/key :class
                   ::se/options [{::se/key :wizard
                                  ::se/selections [{::se/key :levels
                                                    ::se/options [{::se/key :level-1 ...}]}]}]}
                  {::se/key :ability-scores
                   ::se/option {::se/key :standard-roll
                                ::se/map-value {:orcpub.dnd.e5.character/str 15 ...}}}]
 ::se/values     {:orcpub.dnd.e5.character/character-name "Fizban"
                  :orcpub.dnd.e5.character/xps 6500
                  :orcpub.dnd.e5.character/custom-equipment [...]
                  ...}
 ::se/homebrew-paths {...}}                      ; which option paths came from homebrew
```

Rules (from `strict.cljc` and `entity.cljc:82-221`):

- A selection has `::se/key` and exactly one of `::se/option` (single) or
  `::se/options` (multi). An option has `::se/key` and optionally
  `::se/int-value`, `::se/string-value`, `::se/map-value`, `::se/selections`.
- Keys are unqualified keywords that don't start with a digit.
- Selection **order is significant** — the old engine folds modifiers in
  traversal order and uses `array-map` specifically to preserve it
  (`entity.cljc:168-171`).
- The old server validates against the generic `::se/entity` spec only — it
  never checks that keys refer to real content. So the *server* accepts any
  well-formed tree; it's the *client engine* that must interpret the keys.

### Read tolerance (must accept)

Every quirk below exists in real saved data. Sources: `character.cljc`
`from-strict`/`to-strict` (lines 254-332), `routes.clj:930-938`,
`entity.cljc:151-180`.

| # | Quirk | Required behavior |
|---|-------|-------------------|
| R1 | Equipment stored as a **map** `{item-kw value}` in old saves, as a **vector** of `{key value}` in new ones, under all seven equipment keys (`:equipment :weapons :armor :treasure :other-magic-items :magic-weapons :magic-armor`) | Accept both; normalize to vector (`vectorize-equipment`, `character.cljc:274`) |
| R2 | `::char5e/prepared-spells-by-class` stored as a **seq of records** `[{::class-name ".." ::prepared-spells [...]}]` | Convert to `{class-name #{spell-keys}}` |
| R3 | `::spells/slots-used` and `::features-used` values stored as vectors; `::features-used` may carry a stray `:db/id` | Convert values to sets; drop `:db/id` |
| R4 | Option `::se/int-value` of `0` and `::se/string-value` of `""` round-trip to **nil** in the old code (`(or int-value map-value string-value)`) | Treat missing and zero/empty as equivalent where the old app did |
| R5 | `::char5e/xps` may arrive as a **string** (server coerces on save; old records may predate that) | Parse; blank/invalid → 0 |
| R6 | `::equip/quantity` may be a string (`"2"`) | Parse; non-`\d+` → 0 |
| R7 | Very old saves use **unqualified** keys (`:str`, `:quantity`) instead of `::char5e/str`, `::equip/quantity` | Detect (`character.cljc:47-94` specs) and namespace them — the old app's migration is disabled, so a rewrite that wants to read these must re-enable the logic |
| R8 | Selection keys that no longer resolve (content from a homebrew pack that isn't loaded) | Keep the choices; surface as "missing content" (`content_reconciliation.cljs` behavior) — never silently drop |
| R9 | Multi-select options carry no index in their path; **duplicates by key** can occur (`has-duplicate-selections?`) | Tolerate on read; never produce on write |
| R10 | `::char5e/share?` is exposed as `public?` in the old accessor layer | Name mismatch only; preserve the stored key |

### Write canonical form

- Always the vector form for equipment (R1), record form for prepared spells
  (R2) — i.e. exactly what the old `to-strict` emits, since the old server
  and old client both expect it.
- Strip `::image-url-failed` / `::faction-image-url-failed` (transient UI
  state) and NaN numerics, as `clean-values` does (`character.cljc:254`).
- Preserve `:db/id` on the root **and on nested option/selection nodes**
  where present — the old server upserts by them; losing them creates
  duplicate rows on save.
- Never emit an option key that isn't in the new app's content registry —
  the write side is strict even though the read side is lenient.

### Fixtures

- `test/cljc/orcpub/dnd/e5/character_test.clj:100-113` — three real Datomic
  entities (barbarian; large multiclass fighter/eldritch-knight with feats,
  magic items, treasure; warlock/druid). Round-trip `read → write` must be
  structurally identical.
- Phase-0 captures of `GET /dnd/5e/characters/:id` from a running old
  instance (see Plan Set 1 doc 01) for each golden character.
- Synthetic fixtures for R1–R9, one per quirk.

## C2. Content identity — the key namespace

Saved characters reference content only by keyword key
(`:elf`, `:wizard`, `:acid-arrow`, `:longsword`, `:champion`). The new app's
content registry must use **identical keys** for every SRD entity, and the
new engine's **selection structure** (which selections exist, what their keys
are, how they nest) must match the old template closely enough that old
option paths resolve.

The key-derivation rule is `common/name-to-kw` (`src/cljc/orcpub/common.cljc`):
lowercase, non-alphanumerics to `-`, collapsed. The 16 spells with explicit
overriding keys (`spells.cljc:82,271,300,...` — e.g. `:hideous-laughter`,
`:tiny-hut`, `:arcane-sword`) must be carried as explicit keys, not derived.

Selection keys that must be preserved (non-exhaustive; the full list is
extracted in doc 03): `:race`, `:subrace`, `:class`, `:levels`, `:level-N`,
`:background`, `:ability-scores`, `:feats`, `:languages`, `:skill-profs`,
`:tool-profs`, `:fighting-style`, `:eldritch-invocations`, `:pact-boon`, the
subclass selection whose key is `(name-to-kw subclass-title)` (e.g.
`:martial-archetype`, `:arcane-tradition`, `:otherworldly-patron`), equipment
selections (`:weapons`, `:armor`, `:equipment`, `:treasure`, `:magic-weapons`,
`:magic-armor`, `:other-magic-items`), spell selections keyed per class.

**Ref selections** (`::t/ref`) store their data at a global path rather than
their tree position — `[:languages]`, `[:class :warlock :eldritch-invocations]`
etc. (`options.cljc:809, 3091`). The new engine must read/write these paths,
not the tree path. Doc 04 covers the mechanism.

## C3. Homebrew — `.orcbrew` files

Full spec in doc 06. Contract summary:

- **Read**: plain EDN, two accepted top-level shapes (single-plugin keyed by
  content-type keyword; multi-plugin keyed by pack-name string), 13 content
  types, and the ten de-facto drift forms the old importer auto-cleans
  (spurious `nil nil` pairs, `:disabled? nil`, empty option-pack, trailing
  commas, smart quotes, missing names, string vs keyword `:size`, short vs
  namespaced ability keys, …). The new importer must accept every file the
  old one accepts.
- **Write**: EDN (not JSON), canonical strict form: multi-plugin for
  "export all", single-plugin for one pack; `:key` present and equal to the
  map key; non-empty `:option-pack`; ASCII-normalized strings; no `nil` in
  numeric fields. Output must pass the old app's `::e5/plugins` spec.
- **Mechanics fidelity**: a homebrew class/race/feat must produce the same
  computed character in both apps. The `:props`, `:level-modifiers`,
  `:level-selections`, `:spellcasting`, `:traits` vocabularies are the
  contract.
- **Not covered**: magic items are *not* an orcbrew content type (they live
  server-side); `:boons` are half-supported in the old importer. Doc 06 lists
  the old bugs the new app should fix rather than replicate.

Fixtures: `test/duplicate-external-{a,b}.orcbrew`, plus community packs
gathered during Phase 0, plus one synthetic file per drift form.

## C4. PDF sheets

Full spec in doc 07. Contract: the new app fills the same
`resources/fillable-char-sheetstyle-<1..4>-<0..6>-spells.pdf` templates with
the same AcroForm field names (`str-mod`, `acrobatics-check`,
`spells-3-7-2`, …), so a sheet printed by either app is visually identical
for the same character. Fixture: golden-character PDFs captured from the old
app in Phase 0, compared field-by-field (not byte-by-byte — PDFBox and a JS
PDF library differ in byte layout).

## C5. Backend interoperability

Full spec in doc 05. Contract: the new app has an **API adapter** that speaks
the old backend's Transit/JWT API (Plan Set 1 doc 02 is the endpoint map), so
an operator can point the new frontend at an existing server and users see
their existing characters, parties, folders, and magic items. When the new
backend exists, it imports from an old server through the same adapter.

## Known quirks — which side wins

Where the old app's behavior is arguably a bug, the contract picks a side:

| Old behavior | New app does | Why |
|---|---|---|
| `best-weapon-damage-modifier` ignores its `finesse?` argument (`character.cljc:641`) | Fix (use it) | Output value only; not persisted |
| Weapon proficiency returns literal `[:simple :martial]` when `:martial` present (`character.cljc:443`) | Fix (return the set) | Display only |
| `rename-key-in-plugin` only rewrites `:class`/`:race` references on homebrew rename; spells' `:spell-lists` are left stale | Fix (rewrite all references) | Strictly better; old files unaffected |
| Multi-plugin import skips per-item validation | Fix (validate uniformly) | Lenient read is preserved by auto-clean, not by skipping validation |
| A single invalid entry in localStorage wipes **all** homebrew on reload (`db.cljs:244-265`) | Fix (quarantine the entry) | Data-loss bug |
| Human race weighted 3× in random names; Forgotten Realms name tables | Do not port (non-SRD); use SRD-safe or original name lists | Licensing |
| `:boons` missing from import required-fields / names | Fix | Add to the content-type table |
| Anything that changes what gets **written** to a character | Match old output exactly | Old app must read it back |
