# 03: Character import and storage

This document covers how a user gets their characters out of an old
instance, how the new app imports them, and what the new app's own
character format is. It implements contract C2.

## Getting characters out of the old app

The old app has no export button. There are two user-side paths, and
neither needs anything from the operator.

**Path A, the public character URL.** Every character has a server route,
`GET /dnd/5e/characters/<id>` (`routes.clj:1457-1458`), that needs no auth
and returns the strict entity as Transit-JSON. The `<id>` is in the
character's page URL, `/pages/dnd/5e/characters/<id>`. The user opens the
API URL in a browser tab, saves the response as a file, and imports it.
This works today but is tedious for many characters.

**Path B, an exporter bookmarklet. Ship this.** The new project publishes
a bookmarklet, or a small browser extension. The user runs it on the old
site while logged in, and it uses the page's own session. The old client
stores its user map as an EDN string under the localStorage key `"user"`
(`db.cljs:33, 174-176`). The JWT is the `:token "…"` entry in that string,
and a regex can extract it. With the token, the bookmarklet calls
`GET /dnd/5e/characters` with the header `Authorization: Token <jwt>`,
which returns all of the user's characters as full entities, and
`GET /dnd/5e/items` for their custom magic items. It then downloads one
`dmv-export.json` bundle:

```json
{ "format": "dmv-export", "version": 1, "exportedFrom": "https://old.example",
  "characters": [ <transit-decoded strict entity>, ... ],
  "magicItems":  [ ... ] }
```

The calls are same-origin, so CORS is not a problem. The old server sends
no CORS headers in production, so a cross-origin fetch from the new app is
not an option. The bookmarklet either decodes Transit with `transit-js` or
ships the raw Transit for the new app to decode through the library. Keep
it under about 100 lines and free of dependencies where possible. It is the
single most valuable compatibility tool for real users.

**Magic items.** Custom magic items are per-user server data in the old
app, not part of `.orcbrew`. Path B captures them. Path A does not, because
`GET /dnd/5e/items/:id` is public but the list is not. On import they
become homebrew magic items in the new app.

## Importing

`importCharacter` (the doc 02 facade) accepts a strict entity as Transit
text, decoded Transit, or the new app's JSON, and applies these steps in
order:

1. Decode Transit, if needed.
2. Apply the inherited `char5e/from-strict` normalizations (R1 to R3, R6,
   and R9).
3. Apply the two additions: parse a string `xps` to an integer (R5), and
   migrate legacy unnamespaced keys to namespaced ones (R7, the re-enabled
   migration). Both must run before anything reaches `to-strict`. That
   function drops a string `xps`, and on an unmigrated entity it throws on
   the JVM and zeroes quantities without warning in JS
   (`fixtures/README.md` finding 7).
4. Reconcile missing content (R8). Check every option key against the
   current template, which is the SRD plus the loaded homebrew. Report
   unresolved keys with suggestions from the `content_reconciliation.cljs`
   scoring, so the user can remap them or import the relevant `.orcbrew`
   first. The import is never blocked, and choices are never dropped
   without a report.
5. Strip ownership: remove `::se/owner` and `:db/id`. The new app assigns
   its own ids and keeps the old id as `legacyId` metadata for reference.

In the UI, recommend that users import homebrew first and characters
second, so that step 4 has the content it needs.

## The new app's native character format

Store characters as the strict entity: JSON with string keys, with
namespaced keyword names preserved as strings such as
`"orcpub.entity.strict/key"`. There are three reasons. It is what the
engine consumes and produces. It is small, because it is only a choice
tree. And it makes import a normalization step rather than a translation.
Wrap it in an envelope the app owns:

```json
{ "format": "dmv-character", "version": 1,
  "id": "…", "name": "Fizban", "updatedAt": "…",
  "entity": { "orcpub.entity.strict/selections": [...], "orcpub.entity.strict/values": {...} } }
```

Export from the new app is this envelope for a single character or the
`dmv-export` bundle above for all characters plus homebrew. These are
new-app formats. The old app cannot read them and does not need to.

One ordering rule (doc 02 wrinkle 3): serialize selections as arrays, never
as key-ordered objects, so that selection order, which affects modifier
order, survives every round-trip.

## Storage

- **Local-first.** IndexedDB, with one record per character plus an index
  of summaries (name, race, class and level, portrait) for the list page.
  Autosave drafts as the old app does (`autosave_fx.cljs`).
- **With the new backend** (doc 05). The same envelope as an opaque document
  per character, with an owner, timestamps, and the summary projection.
  Those are the three things the old Datomic schema also keeps
  (`db/schema.clj:164-203`). Nothing about the old schema, API, or Transit
  is carried over.

## Tests

- Round-trip: `exportCharacter(importCharacter(x))` structurally equals
  `char5e/to-strict(char5e/from-strict(x))` for the `character_test.clj`
  entities and all M0 captures.
- One fixture per quirk from R1 to R9.
- Bookmarklet: an integration test against an old instance running in
  Docker (`docker-compose up` in this repo) with a seeded user and
  characters.

## Deliverables

Linear tracks the deliverables. On PubDoor, `importCharacter` and the
legacy fixtures are M1 (ORC-20, ORC-21), and `reconcileMissingContent` is
M3 (ORC-33). On Alchemy 5e, the native envelope, IndexedDB storage, and
file export and import are M2 (ORC-47, ORC-49, ORC-50). The exporter
bookmarklet, its integration test, the migration guide, and the
reconciliation UI are M5 (ORC-66 to ORC-68, ORC-71).
