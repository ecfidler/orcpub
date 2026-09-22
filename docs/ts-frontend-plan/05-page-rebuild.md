# Phase 4: Rebuild the pages one by one

The goal is feature parity with the pages that matter, built in dependency
order. Each page lists its old-code reference, for behavior rather than
for porting, and its API and engine dependencies.

Page URL paths mirror `src/cljc/orcpub/route_map.cljc` (`/pages/...`) so
that existing bookmarks and shared character links keep working.

## Tier 1: the core loop, which is the product

### 4.1 Character list and summaries
- Old code: the character list views in `views.cljs` and the events for
  list loading.
- API: `GET /dnd/5e/character-summaries` and
  `DELETE /dnd/5e/characters/:id`.
- Done in M1 (doc 04). Add delete and the folder filter here.

### 4.2 Character sheet: the read-only view and the shareable page
- Old code: the character display sections of `views.cljs`.
  `src/cljc/orcpub/views_aux.cljc` has shared display helpers.
- Engine: `buildCharacter` plus display formatting. The
  `dnd/e5/display.cljc` logic is small enough to reimplement in
  TypeScript, or expose it through the facade.
- Public route: `/pages/dnd/5e/characters/:id` works without
  authentication, because `get-character` is public.

### 4.3 The character builder, the largest page
- Old code: `character_builder.cljs` (87 KB) is the spec. `events.cljs`
  and `subs.cljs` hold the update loop.
- Engine: `availableSelections`, `selectOption`, `setValue`, and a
  debounced `buildCharacter`, plus `randomCharacter` for the "random"
  button.
- Break it into sub-milestones, each of which can ship behind the M1
  sheet:
  1. **Selection pane shell.** Render `availableSelections` as the stepper
     (race, background, class, abilities, and so on). Picking an option
     round-trips through the engine, with the live sheet preview beside
     it.
  2. **Ability scores.** Standard rolls, point buy, and manual entry. See
     `standard-ability-rolls` in `character.cljc`, the point-buy rules in
     the template, and the `::values` path for manual entry.
  3. **Class and level management.** Adding and removing multiclass
     entries, and per-level selections (the `::options` multi-select shape
     with `::map-value` levels).
  4. **Equipment and spells panes.** Equipment is partly in `::values`
     (custom items) and partly in selections. Spells known and prepared
     are per class.
  5. **Description and details pane.** Free-form `::values` fields.
  6. **Save, load, and autosave.** The `POST /dnd/5e/characters` upsert,
     dirty tracking, and localStorage draft recovery.
- Validation: the old builder shows unfilled selections ("2 choices
  remaining"). The `min` and `max` in `availableSelections` carry what is
  needed.
- Parity test: rebuild each golden character from scratch in the new
  builder. The saved entity must round-trip and build to identical values.

### 4.4 PDF export

See doc 06. It is small. Do it right after 4.2.

## Tier 2: organization and social

### 4.5 Parties
- Old code: the party views in `views.cljs`, and `routes/party.clj` on the
  server.
- API: the six `/dnd/5e/parties` endpoints (doc 02).

### 4.6 Folders
- The same shape as parties. Folders appear in the character list as
  filters.

### 4.7 Account pages
- The my-account page (email change, preferences, delete), the password
  reset flows, and the verification screens. All are plain forms over the
  user endpoints.

## Tier 3: content browsing, static data with no backend

### 4.8 Spell, monster, and magic-item lists and detail pages
- Old code: the list and detail views in `views.cljs`.
- Data: either expose search and list functions from the engine facade,
  since the data is already in the bundle, or dump the clean data files
  (`spells.cljc`, `monsters.cljc`, `magic_items.cljc`) to static JSON at
  engine build time and render from that. The JSON dump is recommended,
  because it lets these pages be split away from the engine chunk.

## Tier 4: the homebrew builders

### 4.9 The 14 content builders
The builders for race, subrace, class, subclass, background, feat, spell,
monster, magic item, language, invocation, boon, selection, and encounter
(`route_map.cljc:183-196`), plus the "my content" page and `.orcbrew`
import and export (doc 06).

- Old code: the builder forms in `views.cljs`, the option-building events
  in `events.cljs`, and the validation in `import_validation.cljs`.
- Storage: magic items go through the API (`/dnd/5e/items`). All other
  homebrew is client-side, in localStorage and `.orcbrew` files. Replicate
  that model.
- These are mostly CRUD forms over well-defined shapes. Ship them one at a
  time, in usage order: spell, monster, race and subclass, then the rest.
- Homebrew content must reach the engine. `withHomebrew` (doc 03) extends
  the template, as the old app merges plugins into the template.

### 4.10 Combat tracker and encounter builder, optional and last
- Old code: `combat.cljc`, `encounters.cljc`, and the views in
  `views.cljs`.
- Self-contained. Decide at the time whether they are worth porting.

## Out of scope

- The "Orcacle" page (`dnd-e5-orcacle-page-route`), unless you use it.
- The newb character builder variant (`newb-character-builder`). Fold its
  good ideas into 4.3 instead of building two builders.

## Exit criteria per page

A page ships when it works against the real backend, passes a visual and
behavioral spot check against the reference app, leaves the golden
characters unaffected (the engine tests are still green), and has one
Playwright test covering its happy path.
