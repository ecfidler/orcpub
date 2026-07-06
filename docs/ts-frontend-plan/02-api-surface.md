# Phase 1 — The Backend API Surface

Goal: a complete, verified map of every endpoint the new frontend needs, plus a
decision on wire format. The backend does not change in this plan.

Sources of truth in this repo:

- `src/cljc/orcpub/route_map.cljc` — URL paths, defined once and shared
- `src/clj/orcpub/routes.clj` — HTTP verbs, interceptors (auth/ownership),
  handlers (route table starts around line 1418)
- `src/clj/orcpub/routes/party.clj`, `src/clj/orcpub/routes/folder.clj`

## Wire format: Transit

API endpoints exchange **Transit-JSON** (`Content-Type:
application/transit+json`). Pedestal's `body-params` interceptor decodes
request bodies into `:transit-params`; responses are Transit-encoded Clojure
data with namespaced keyword keys (e.g. `:orcpub.entity.strict/selections`).

Use Cognitect's official [`transit-js`](https://github.com/cognitect/transit-js)
(and `transit-immutable-js` if desired). Write one thin codec module that:

- encodes plain JS objects → Transit for request bodies
- decodes Transit → plain JS objects, converting namespaced keywords to string
  keys (recommended convention: keep the full namespace, e.g.
  `"orcpub.entity.strict/key"`, so round-trips are lossless)

Test the codec against the Phase 0 fixtures: decode → encode must produce an
equivalent payload the server accepts.

## Authentication

- `POST /login` with `{username, password}` (username or email both work — see
  `lookup-user`, routes.clj:136). Response contains `:token` (JWT) and user
  data (routes.clj:271).
- Authenticated requests send the header: `Authorization: Token <jwt>`
  (see `auth-headers` in `src/cljc/orcpub/dnd/e5/event_utils.cljc`).
- Tokens are Buddy JWS tokens signed with the `SIGNATURE` env var; expired or
  invalid tokens get a 401 from the `check-auth` interceptor
  (routes.clj:145). The current client stores the token in app state /
  localStorage; do the same and treat any 401 as "redirect to login".

## Endpoint map

Auth column: ✓ = requires `Authorization` header; ✓+own = also checks resource
ownership (401/404 otherwise).

### Users & auth

| Method | Path | Auth | Handler (routes.clj) |
|--------|------|------|----------------------|
| POST | `/register` | – | `register` |
| POST | `/login` | – | `login` |
| GET | `/user` | ✓ | `get-user` |
| PUT | `/user` | ✓ | `update-user-preferences` |
| DELETE | `/user` | ✓ | `delete-user` |
| PUT | `/user/email` | ✓ | `request-email-change` |
| GET | `/check-email` | – | email availability |
| GET | `/check-username` | – | username availability |
| GET | `/verify` | – | email verification link |
| GET | `/re-verify` | – | resend verification |
| POST | `/reset-password` | ✓ (cookie) | `reset-password` |
| GET | `/send-password-reset` | – | `send-password-reset` |
| GET | `/unsubscribe` | – | email unsubscribe |
| POST | `/following/users/:user` | ✓ | `follow-user` |
| DELETE | `/following/users/:user` | ✓ | `unfollow-user` |

### Characters

| Method | Path | Auth | Handler |
|--------|------|------|---------|
| GET | `/dnd/5e/characters` | ✓ | `character-list` (full entities) |
| POST | `/dnd/5e/characters` | ✓ | `save-character` (create & update) |
| GET | `/dnd/5e/characters/:id` | – | `get-character` (public read) |
| DELETE | `/dnd/5e/characters/:id` | ✓ | `delete-character` |
| GET | `/dnd/5e/character-summaries` | ✓ | `character-summary-list` (list views) |
| POST | `/character.pdf` | – | `character-pdf-2` (see doc 06) |

Save semantics: `POST /dnd/5e/characters` upserts — a character with a `:db/id`
updates, without one creates. The body is a **strict entity**
(`::orcpub.entity.strict/entity` spec) and is server-side spec-validated;
invalid payloads get a 400 with `spec/explain-data` (routes.clj:946).

### Parties (`src/clj/orcpub/routes/party.clj`)

| Method | Path | Auth |
|--------|------|------|
| GET | `/dnd/5e/parties` | ✓ |
| POST | `/dnd/5e/parties` | ✓ |
| DELETE | `/dnd/5e/parties/:id` | ✓+own |
| PUT | `/dnd/5e/parties/:id/name` | ✓+own |
| POST | `/dnd/5e/parties/:id/characters` | ✓+own |
| DELETE | `/dnd/5e/parties/:id/characters/:character-id` | ✓+own |

### Folders (`src/clj/orcpub/routes/folder.clj`) — same shape as parties

`/dnd/5e/folders`, `/dnd/5e/folders/:id`, `/dnd/5e/folders/:id/name`,
`/dnd/5e/folders/:id/characters[/:character-id]`.

### Homebrew magic items

| Method | Path | Auth |
|--------|------|------|
| GET | `/dnd/5e/items` | ✓ | own item list |
| POST | `/dnd/5e/items` | ✓ | save (spec-validated) |
| GET | `/dnd/5e/items/:id` | – | public read |
| DELETE | `/dnd/5e/items/:id` | ✓ |
| GET | `/dnd/5e/item-summaries` | ✓ |

Other homebrew content types (classes, races, spells, monsters, etc.) are
**not** stored via the API — they live client-side (localStorage) and in
`.orcbrew` files. See doc 06.

## The strict entity format

This is the payload for saving/loading characters. Spec:
`src/cljc/orcpub/entity/strict.cljc`. Shape (namespaced keys elided):

```clojure
{:db/id       17592186045432          ; present on updates
 ::selections [{::key :race
                ::option {::key :elf
                          ::selections [{::key :subrace
                                         ::option {::key :high-elf}}]}}
               {::key :class
                ::options [{::key :wizard
                            ::map-value {...}   ; per-option data (e.g. levels)
                            ::selections [...]}]}]
 ::values     {:orcpub.dnd.e5.character/character-name "Fizban" ...}}
```

A selection has a `::key` and either one `::option` or many `::options`; each
option has a `::key` and optional `::int-value` / `::map-value` / nested
`::selections`. Free-form fields live in `::values`. The engine converts
between this wire format and its internal format via `to-strict`/`from-strict`
(`src/cljc/orcpub/entity.cljc` and `src/cljc/orcpub/dnd/e5/character.cljc`) —
the facade in doc 03 exposes those, so the TS app never builds this by hand.

## Deliverables

- [ ] `web-ts/src/api/` client module: typed functions per endpoint above
- [ ] Transit codec with round-trip tests against Phase 0 fixtures
- [ ] Auth/token handling (store, attach header, 401 → login)
- [ ] Optional: an OpenAPI-style markdown doc generated from this table for
      contributors

## Optional backend nicety (deferred)

A JSON content negotiation layer server-side would remove the Transit
dependency, but it means Clojure work and a second format to keep compatible.
Skip it; `transit-js` is well-maintained and the codec is ~100 lines.
