# Complete Rewrite with Content Compatibility — Plan (Plan Set 2)

A plan for building a **new web application from scratch** — TypeScript rules
engine, TypeScript/React UI, and (eventually) its own backend — that stays
**cross-compatible** with the existing Dungeon Master's Vault / orcpub app:
characters, homebrew content, and printed sheets move between the two apps in
both directions.

This plan set is independent of Plan Set 1 (`docs/ts-frontend-plan/`, which
reuses the compiled Clojure engine). Where Plan Set 1's documents still apply
verbatim (API surface, app scaffold, page rebuild order), this set references
them instead of repeating them.

## What "cross-compatible" means here

Five concrete contracts, fully specified in
[01-compatibility-contract.md](01-compatibility-contract.md):

1. **Characters** — the new app reads every character the old app ever saved
   (the *strict entity* choice tree, including legacy quirks) and writes
   characters the old app can load.
2. **Homebrew** — `.orcbrew` files import into the new app with the same
   leniency the old importer has, and export from it in a form the old app
   accepts.
3. **Content identity** — races, classes, spells, items, etc. keep the **same
   keyword keys** as the old app, because saved characters and homebrew files
   reference content by key.
4. **PDF sheets** — the new app fills the same 28 fillable PDFs with the same
   field names.
5. **Backend** — the new app can run against an existing orcpub server
   (Transit API, JWT auth) via an adapter, so an operator can switch frontends
   without migrating data; a new backend, when built, imports from it.

## What the investigation established

These findings, verified against the source, shape everything below:

- **The rules engine is code, not data.** It is a dependency-ordered fold of
  ~100 modifier constructors over ~110 lazily-derived attributes, with class
  features level-gated inside Clojure macro bodies. It cannot be exported; it
  must be re-expressed. See [04-rules-engine.md](04-rules-engine.md).
- **Shipped content is SRD 5.1 only, and small.** 12 classes + 12 subclasses,
  9 races (~20 subraces), 1 background, 1 feat, 6 fighting styles, 33
  invocations, 3 pact boons. Everything non-SRD in the source is disabled and
  is excluded here. See [03-content-extraction.md](03-content-extraction.md).
- **~1,000 content entries are pure data** (268 spells, 317 monsters, 45
  weapons, 14 armors, ~160 equipment items, 18 skills, 16 languages, spell
  lists) and export to JSON mechanically. Magic items (288) are two-thirds
  data with ~120 modifier call sites to translate.
- **Homebrew already has a declarative mechanics vocabulary** (`:props`,
  `:level-modifiers`, `:spellcasting`, `:traits`). The new content schema is
  designed as a superset of it, so `.orcbrew` import becomes a mapping rather
  than a translation. See [02-content-schema.md](02-content-schema.md).
- **The output surface is fully enumerable**: ~100 computed attributes plus
  a fixed PDF field catalogue. See [04](04-rules-engine.md) and
  [07-pdf-export.md](07-pdf-export.md).
- **Golden data already exists in the repo**: three real Datomic character
  entities in `test/cljc/orcpub/dnd/e5/character_test.clj` and an end-to-end
  build test in `warlock_test.clj`.

## Plan documents

| Doc | Summary |
|-----|---------|
| [01-compatibility-contract.md](01-compatibility-contract.md) | The five contracts, precisely: what must round-trip and what may be dropped |
| [02-content-schema.md](02-content-schema.md) | The new declarative content format that both SRD content and homebrew compile to |
| [03-content-extraction.md](03-content-extraction.md) | Getting SRD content out of the Clojure source: mechanical export + hand re-authoring |
| [04-rules-engine.md](04-rules-engine.md) | The TypeScript engine: attribute graph, modifier application, computed surface, golden tests |
| [05-character-persistence.md](05-character-persistence.md) | Reading/writing strict entities, the API adapter to the existing backend, the future backend |
| [06-homebrew-orcbrew.md](06-homebrew-orcbrew.md) | EDN in TypeScript; lenient import, canonical export, validation, conflicts |
| [07-pdf-export.md](07-pdf-export.md) | Filling the existing PDFs: field map, page selection, browser-side vs server-side |
| [08-app-and-ui.md](08-app-and-ui.md) | The application layer, by reference to Plan Set 1 plus what differs |
| [09-milestones-and-risks.md](09-milestones-and-risks.md) | Sequencing, definitions of done, risk register |

## Ground rules

- The new app lives in its own repository (or a top-level directory such as
  `dmv-next/`). It never imports Clojure code. This repository is used as
  **reference and test oracle** only.
- Content compatibility is enforced by tests, not intentions: every contract
  in doc 01 has a fixture set and a test that runs in CI.
- Prefer reproducing the old app's *behavior* over its *structure*. Where the
  old app has a bug that saved data depends on, the contract says which side
  wins (doc 01 §"Known quirks").
- SRD only. Non-SRD content enters only through homebrew files, as today.
