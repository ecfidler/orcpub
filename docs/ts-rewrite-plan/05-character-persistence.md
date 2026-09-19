# 05 — Character Persistence and Backend

How the new app stores characters, talks to an existing orcpub server, and
eventually owns its own backend — without ever breaking contract C1 or C5.

## The strict entity is the storage format

The new app persists characters **as strict entities** (contract C1), not as
some new normalized model. Reasons: it is what the old server stores, it is
what both apps must exchange, and it is small (a choice tree, not a computed
sheet). A normalized model would need a lossless mapping back to it anyway.

`packages/entity` provides:

- `parseStrict(data): Entity` — applies every read tolerance R1–R10 from
  doc 01 and returns the canonical internal form. Each quirk is a named,
  unit-tested normalizer (`vectorizeEquipment`, `preparedSpellsToSets`,
  `namespaceLegacyKeys`, …).
- `toStrict(entity): StrictEntity` — the canonical write form, byte-for-byte
  what the old `to-strict` emits (R1/R2 record forms, transient fields
  stripped, `:db/id` preserved on every node).
- Transit codec: strict entities on the wire are Transit-JSON with
  namespaced keyword keys. Decode keywords to strings of the form
  `"orcpub.entity.strict/key"`; encode back. `transit-js` is the official
  implementation (Plan Set 1 doc 02 §"Wire format").
- Round-trip tests against the `character_test.clj` fixtures and every
  Phase-0 capture: `toStrict(parseStrict(x))` ≡ `oldToStrict(oldFromStrict(x))`.

## Backend adapter — running against an existing orcpub server

The endpoint map is Plan Set 1 doc 02 (Transit body format,
`Authorization: Token <jwt>`, the character / party / folder / item
endpoints). The adapter is a `BackendPort` interface with one implementation
per backend:

```ts
interface BackendPort {
  login(user, pass): Promise<Session>; register(...); user(): Promise<User>;
  listCharacterSummaries(): Promise<Summary[]>;
  getCharacter(id): Promise<StrictEntity>;      // public read
  saveCharacter(e: StrictEntity): Promise<{ id }>;
  deleteCharacter(id);
  parties(), createParty(), ...; folders(), ...;
  items(), saveItem(), deleteItem();            // homebrew magic items
  characterPdf(fields, style, spellPages): Promise<Blob>;  // doc 07
}
```

`OrcpubBackend` implements it over the old API. Details to get right:

- `POST /dnd/5e/characters` upserts by `:db/id`; the server runs `spec/valid?
  ::se/entity` and returns 400 with `explain-data` on failure — surface that.
- The server rewrites `::char5e/xps` from string to long on save and may
  return characters that predate that (R5).
- `GET /dnd/5e/characters/:id` is public (no auth); the list endpoints need
  auth. Parties/folders check ownership (401/404).
- Magic items live server-side (`/dnd/5e/items`) and are *not* in
  `.orcbrew` files — the adapter is the only source for a user's custom
  items on an old server.
- Registration/verification/password-reset are redirect-driven flows
  (`/verify`, `/reset-password` with cookies). Either reuse the old server's
  pages for these or reimplement the forms; both work with the adapter.

Outcome: **Milestone "drop-in frontend"** — the new app, pointed at an
existing server via the adapter, shows all of a user's characters, builds
and saves them, prints PDFs, and the old frontend still works on the same
data. This is the cross-compatibility proof and it doesn't require a new
backend at all.

## Local-first mode

Independent of any server, the new app should work with no backend:
characters in IndexedDB (strict entity JSON), homebrew in IndexedDB, export
and import via files (`.json` for characters — a new format the old app
can't read — plus `.orcbrew` for content). This is the mode a self-hoster
gets before choosing a backend, and it's what makes the app testable
end-to-end without a Datomic transactor.

## The new backend (later)

When it's time to own the server side, keep the contract by design:

- **Storage**: characters as opaque strict-entity documents (JSON/JSONB) with
  owner, timestamps, and a `summary` projection for list views — the same
  three things the old Datomic schema keeps (`db/schema.clj:164-203`
  `entity-schema` + `::se/summary`). Do **not** normalize the choice tree
  into relational tables.
- **API**: JSON, not Transit. The `BackendPort` interface absorbs the
  difference; the entity payload is the same shape either way.
- **Auth**: standard email/password + JWT, or OIDC. The old server's Buddy
  JWTs are not portable (secret is per-instance) — migration re-registers or
  forces password reset.
- **Users, parties, folders, items**: the old schema is small
  (`db/schema.clj:111-162` users; `:317-337` parties/folders; `:349-376`
  magic items). Mirror it.

### Migration from an old server

Use the adapter, not the database: log in as each user (or use an admin
export), pull `GET /dnd/5e/characters` (full entities), parties, folders,
items, and write them through the new backend's API. This works against any
running old instance without touching Datomic. For a self-hoster migrating
their own instance, ship it as a CLI: `dmv migrate --from https://old
--user ... --to https://new`.

Character `:db/id`s become opaque external ids on the new side; keep them in
a `legacy_id` column so links like `/pages/dnd/5e/characters/17592186045432`
can redirect.

## Deliverables

- [ ] `packages/entity`: `parseStrict`/`toStrict` + normalizers + Transit codec,
      round-trip tests green on all fixtures
- [ ] `BackendPort` + `OrcpubBackend` + `LocalBackend` (IndexedDB)
- [ ] "Drop-in frontend" milestone demonstrated against a real old server
- [ ] (later) new backend + `dmv migrate` CLI
