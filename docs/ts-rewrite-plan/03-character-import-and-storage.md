# 03 — Character Import and Storage

How a user gets their characters out of an old instance, how the new app
imports them, and what the new app's own character format is. Contract C2.

## Getting characters out of the old app

There is no export button in the old app. Two user-side paths, both
requiring nothing from the operator:

**Path A — the public character URL.** Every character has a server route
`GET /dnd/5e/characters/<id>` (`routes.clj:1457-1458`, no auth) returning
the strict entity as Transit-JSON. The `<id>` is in the character's page URL
(`/pages/dnd/5e/characters/<id>`). The user opens the API URL in a browser
tab, saves the response as a file, and imports it. Works today; clunky for
many characters.

**Path B — an exporter bookmarklet (ship this).** A bookmarklet (or a tiny
browser-extension) the new project publishes. Run on the old site while
logged in, it uses the page's own session — the old client stores its user
map as an EDN string under localStorage key `"user"` (`db.cljs:33, 174-176`),
from which the JWT is a `:token "…"` entry, extractable with a regex — to call
`GET /dnd/5e/characters` (`Authorization: Token <jwt>`; returns *all* of the
user's characters as full entities), plus `GET /dnd/5e/items` for their
custom magic items, and downloads one `dmv-export.json` bundle:

```json
{ "format": "dmv-export", "version": 1, "exportedFrom": "https://old.example",
  "characters": [ <transit-decoded strict entity>, ... ],
  "magicItems":  [ ... ] }
```

Same-origin, so no CORS issue (the old server sends no CORS headers in
production — cross-origin fetch from the new app is not an option). The
bookmarklet decodes Transit with `transit-js` or ships the raw Transit for
the new app to decode via the library. Keep it under ~100 lines and
dependency-free where possible; it is the single most valuable
compatibility artifact for real users.

**Magic items.** Custom magic items are per-user server data in the old app,
not part of `.orcbrew`. Path B captures them; Path A does not
(`GET /dnd/5e/items/:id` is public but the list is not). On import they
become homebrew magic items in the new app.

## Importing

`importCharacter` (doc 02 facade) accepts a strict entity (Transit text,
decoded Transit, or the new app's JSON) and applies, in order:

1. Transit decode (if needed).
2. The inherited `char5e/from-strict` normalizations (R1–R3, R6, R9).
3. The two additions: `xps` string → int (R5); legacy unnamespaced keys →
   namespaced (R7, the re-enabled migration).
4. Missing-content reconciliation (R8): every option key is checked against
   the current template (SRD + loaded homebrew); unresolved keys are
   reported with suggestions (`content_reconciliation.cljs` scoring) and the
   user can remap or import the relevant `.orcbrew` first. The import is
   never blocked and choices are never dropped silently.
5. Ownership: `::se/owner` and `:db/id` are stripped; the new app assigns its
   own ids (keep the old id as `legacyId` metadata for reference).

Recommend the import order to users in the UI: **homebrew first, then
characters**, so step 4 has the content it needs.

## The new app's native character format

Store characters as the **strict entity** (JSON with string keys, namespaced
keyword names preserved as `"orcpub.entity.strict/key"` strings). Reasons:
it is what the engine consumes and produces; it is small (a choice tree);
and it makes import a normalization step rather than a translation. Wrap it
in an envelope the app owns:

```json
{ "format": "dmv-character", "version": 1,
  "id": "…", "name": "Fizban", "updatedAt": "…",
  "entity": { "orcpub.entity.strict/selections": [...], "orcpub.entity.strict/values": {...} } }
```

Export from the new app is this envelope (single character) or the
`dmv-export` bundle above (all characters + homebrew). These are new-app
formats; the old app can't read them and doesn't need to.

Ordering caveat (doc 02 wrinkle 3): serialize selections as **arrays**, never
as key-ordered objects, so selection order — which affects modifier order —
survives every round-trip.

## Storage

- **Local-first**: IndexedDB, one record per character plus an index of
  summaries (name, race, class/level, portrait) for the list page. Autosave
  drafts as the old app does (`autosave_fx.cljs` behavior).
- **With the new backend** (doc 05): the same envelope as an opaque document
  per character, with owner, timestamps, and the summary projection — the
  three things the old Datomic schema also keeps (`db/schema.clj:164-203`).
  Nothing about the old schema, API, or Transit is carried over.

## Tests

- Round-trip: `exportCharacter(importCharacter(x))` structurally equals
  `char5e/to-strict(char5e/from-strict(x))` for the `character_test.clj`
  entities and all Phase-0 captures.
- One fixture per quirk R1–R9.
- Bookmarklet: an integration test against a dockerized old instance
  (`docker-compose up` in this repo) with a seeded user and characters.

## Deliverables

- [ ] Exporter bookmarklet + a one-page "moving from the old app" guide
- [ ] `importCharacter` with reconciliation UI
- [ ] Native envelope + IndexedDB storage + file export/import
- [ ] Fixture suite green
