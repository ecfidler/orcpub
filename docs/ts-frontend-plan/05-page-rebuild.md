# Phase 4 — Page-by-Page Rebuild

Goal: feature parity with the pages that matter, built in dependency order.
Each page lists its old-code reference (for behavior, not for porting) and its
API/engine dependencies.

Page URL paths should mirror `src/cljc/orcpub/route_map.cljc` (`/pages/...`)
so existing bookmarks and shared character links keep working.

## Tier 1 — core loop (this is the product)

### 4.1 Character list & summaries
- Old code: character list views in `views.cljs`; events for list loading
- API: `GET /dnd/5e/character-summaries`, `DELETE /dnd/5e/characters/:id`
- Done in M1 (doc 04); add delete + folder filter here

### 4.2 Character sheet (read-only view + shareable page)
- Old code: character display sections of `views.cljs`;
  `src/cljc/orcpub/views_aux.cljc` has shared display helpers
- Engine: `buildCharacter` + display formatting (`dnd/e5/display.cljc` logic —
  small enough to reimplement in TS, or expose via the facade)
- Public route: `/pages/dnd/5e/characters/:id` works unauthenticated
  (`get-character` is public)

### 4.3 The character builder — the big one
- Old code: `character_builder.cljs` (87KB) is the spec;
  `events.cljs`/`subs.cljs` for the update loop
- Engine: `availableSelections`, `selectOption`, `setValue`, debounced
  `buildCharacter`; `randomCharacter` for the "random" button
- Break it into sub-milestones, each shippable behind the M1 sheet:
  1. **Selection pane shell**: render `availableSelections` as the stepper
     (race, background, class, abilities...); picking options round-trips
     through the engine; live sheet preview beside it
  2. **Ability scores**: standard rolls / point-buy / manual entry
     (`character.cljc` `standard-ability-rolls`, point-buy rules in the
     template; the `::values` path for manual entry)
  3. **Class & level management**: multiclass add/remove, per-level selections
     (the `::options` multi-select shape with `::map-value` levels)
  4. **Equipment & spells panes**: equipment lives partly in `::values`
     (custom items) and partly in selections; spells-known/prepared per class
  5. **Description/details pane**: free-form `::values` fields
  6. **Save/load/autosave**: `POST /dnd/5e/characters` upsert, dirty tracking,
     localStorage draft recovery
- Validation: the old builder surfaces unfilled selections ("2 choices
  remaining"); `availableSelections` min/max carries what's needed
- Parity test: rebuild each golden character from scratch in the new builder;
  saved entity must round-trip and build to identical values

### 4.4 PDF export — see doc 06 (small, do it right after 4.2)

## Tier 2 — organization & social

### 4.5 Parties
- Old code: party views in `views.cljs`; `routes/party.clj` server-side
- API: the six `/dnd/5e/parties` endpoints (doc 02)

### 4.6 Folders
- Same shape as parties; integrates into the character list as filters

### 4.7 Account pages
- My-account (email change, preferences, delete), password reset flows,
  verification screens. All plain forms over the user endpoints.

## Tier 3 — content browsing (static data, no backend)

### 4.8 Spell / monster / magic-item lists and detail pages
- Old code: list/detail views in `views.cljs`
- Data: expose search/list functions from the engine facade (the data is
  already in the bundle), or dump the clean data files
  (`spells.cljc`, `monsters.cljc`, `magic_items.cljc`) to static JSON at
  engine build time and render from that — JSON dump recommended: enables
  code-splitting these pages away from the engine chunk

## Tier 4 — homebrew builders (the long tail)

### 4.9 The ~14 content builders
Race, subrace, class, subclass, background, feat, spell, monster, magic item,
language, invocation, boon, selection, encounter builders
(`route_map.cljc:183-196`), plus the "my content" page and `.orcbrew`
import/export (doc 06).

- Old code: builder forms in `views.cljs`; option-building events in
  `events.cljs`; validation in `import_validation.cljs`
- Storage: magic items via API (`/dnd/5e/items`); all other homebrew is
  client-side (localStorage) + `.orcbrew` files — replicate that model
- These are mostly CRUD forms over well-defined shapes. Ship them one at a
  time, ordered by usage: spell → monster → race/subclass → the rest
- Homebrew content must reach the engine: `withHomebrew` (doc 03) extends the
  template, mirroring how the old app merges plugins into the template

### 4.10 Combat tracker & encounter builder (optional last)
- Old code: `combat.cljc`, `encounters.cljc`, views in `views.cljs`
- Self-contained; decide at the time whether they're worth porting

## Explicitly out of scope

- The "Orcacle" page (`dnd-e5-orcacle-page-route`) unless you use it
- The newb character builder variant (`newb-character-builder`) — fold the
  good ideas into 4.3 instead of building two builders

## Exit criteria per page

Each page ships when: works against the real backend, visual/behavioral spot-
check against the reference app, golden characters unaffected (engine tests
still green), and one Playwright test covering its happy path.
