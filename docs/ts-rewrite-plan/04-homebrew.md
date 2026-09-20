# 04 — Homebrew

`.orcbrew` import and export through the engine library, and the homebrew
features around it. Contract C1. Source of truth for the old behavior is the
homebrew investigation (`import_validation.cljs`, `spell_subs.cljs`,
`events.cljs:3601-4000`, `docs/ORCBREW_FILE_VALIDATION.md`,
`docs/CONFLICT_RESOLUTION.md`).

## What is inherited (compiled into the library)

- **Parsing**: `cljs.reader/read-string` — no JS EDN library needed.
- **The auto-clean pipeline** (`validate-import`, `import_validation.cljs:1266-1376`):
  text-level fixes (`disabled? nil`, `nil nil,` pairs, trailing commas) →
  parse with line-numbered errors → Unicode normalization (`:23-68`) →
  data cleaning (empty pack names, `:option-pack`, `nil` handling per field
  class) → required-field placeholders → selection-option dedup →
  duplicate-key detection (internal and external) → structure validation
  (progressive or strict).
- **The conversion to template options**: `spell_subs.cljs` chain lifted to
  `buildTemplate(homebrew)` (doc 02) — `:props` via `plugin-modifiers`,
  `:level-modifiers` via `level-modifier`, `:level-selections`,
  `:spellcasting` via `class-option`, `:traits` via `traits-modifiers`,
  subclass → class and subrace → race attachment by `:class` / `:race`,
  spell `:spell-lists` inversion. Same code, same results.
- **Export**: `pr-str` of the plugin map; single-plugin for one pack,
  multi-plugin for all; the pre-export validation (`validate-before-export`)
  with the "export anyway" placeholder fill.
- **Missing-content reconciliation** for loaded characters
  (`content_reconciliation.cljs`).

The facade exposes these as `parseOrcbrew(text) → { data, log, conflicts,
skipped }`, `validateForExport(plugins)`, `orcbrewToEdn(plugins, {pretty})`,
`buildTemplate(plugins)`, `reconcileMissingContent(entity, plugins)`.

## What the new app builds in TypeScript

- **Import flow UI**: file picker (`.orcbrew`), import log panel (changes,
  errors, skipped items — the old `:import-log` shape), progressive by
  default with a strict option.
- **Conflict resolution UI** (`docs/CONFLICT_RESOLUTION.md`): per conflict
  rename-import (suggested key = key + slugified source, e.g.
  `:artificer-kibbles-tasty`, from `generate-new-key`), skip, or replace;
  "rename all". Detection and suggestion come from the library; the
  decision application (`rename-key-in-plugin`) is patched to rewrite all
  references (doc 02 patches).
- **Storage**: IndexedDB, one record per pack (name → single-plugin map),
  plus per-pack and per-item enable/disable flags. Invalid entries are
  quarantined with a visible warning, never discarded wholesale. The
  in-memory shape passed to `buildTemplate` is the multi-plugin map, exactly
  as the old app's `:plugins`.
- **"My Content" page**: per pack — enable/disable, export, delete; per type
  — the 13 lists with per-item enable/edit/delete (`views.cljs:7485-7759`
  behavior).
- **Homebrew builders**: the ~14 per-type forms. The old app's builders
  produce plain records validated by per-type specs on save
  (`events.cljs:533-562`, specs in `races.cljc`, `classes.cljc:28`,
  `spells.cljc:45`, `selections.cljc:25`, …). Expose those specs' shape
  through the facade as validation functions and build forms against the
  field tables in `docs/HOMEBREW_REQUIRED_FIELDS.md`. Ship in usage order:
  spell → monster → race/subrace → class/subclass → the rest.
- **Magic items**: a homebrew content type in the *new* app (the old app
  keeps them server-side). Imported via the exporter bundle (doc 03) or
  authored in the item builder (`magic_items_test.clj` documents the
  internal ↔ external item conversion to reuse). On `.orcbrew` export,
  magic items are **omitted** (the old app would ignore an unknown content
  type anyway) or written to a separate new-app file; decide at
  implementation time and document it in the export UI.

## Behavior details to keep

- Subrace `:speed` / `:darkvision` are deltas against the race, applied only
  when different (`options.cljc:1981`).
- Plugin classes skip the built-in ASI and HP selections when `:plugin?`
  (`level-option`) — verify whether that's intended before "fixing" it.
- Class display name becomes `"Name (Source)"` when the source isn't the
  default pack.
- Re-importing a pack over itself is not a conflict; a colliding key from a
  *different* source is.

## Old bugs — fix, don't replicate (from doc 01)

`:boons` half-supported; multi-plugin import skips per-item validation;
rename only rewrites `:class`/`:race`; one bad entry wipes all homebrew;
background `:key` ignored.

## Tests

- Fixture suite: `test/duplicate-external-{a,b}.orcbrew`, community packs,
  one file per drift form; each must import with the same log the old
  importer produces (captured once).
- Lossless: import → export → import yields an identical multi-plugin map.
- Old-app acceptance: every export passes the old `::e5/plugins` spec in a
  REPL (CI job in this repo).
- Mechanics: golden characters using homebrew content evaluate identically.

## Deliverables

- [ ] Import flow + log + conflict UI
- [ ] Storage with quarantine; My Content page
- [ ] Export (per pack, all, pretty) validated against the old spec
- [ ] Builders, in usage order
- [ ] Fixture suite green
