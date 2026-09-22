# Phase 1: Map the backend API

The goal is a complete, verified map of every endpoint the new frontend
needs, plus a decision on the wire format. The backend does not change in
this plan.

Sources of truth in this repo:

- `src/cljc/orcpub/route_map.cljc`: the URL paths, defined once and
  shared.
- `src/clj/orcpub/routes.clj`: the HTTP verbs, the auth and ownership
  interceptors, and the handlers. The route table starts around line 1418.
- `src/clj/orcpub/routes/party.clj` and `src/clj/orcpub/routes/folder.clj`.

## Wire format: Transit

API endpoints exchange Transit-JSON (`Content-Type:
application/transit+json`). Pedestal's `body-params` interceptor decodes
request bodies into `:transit-params`. Responses are Transit-encoded
Clojure data with namespaced keyword keys, for example
`:orcpub.entity.strict/selections`.

Use Cognitect's official [`transit-js`](https://github.com/cognitect/transit-js),
and `transit-immutable-js` if you want it. Write one thin codec module
that:

- encodes plain JS objects as Transit for request bodies, and
- decodes Transit into plain JS objects, converting namespaced keywords to
  string keys. Keep the full namespace, for example
  `"orcpub.entity.strict/key"`, so that round-trips are lossless.

Test the codec against the Phase 0 fixtures. Decoding and then encoding
must produce an equivalent payload that the server accepts.

## Authentication

- `POST /login` with `{username, password}`. Both a username and an email
  work (see `lookup-user`, routes.clj:136). The response contains
  `:token`, a JWT, and the user data (routes.clj:271).
- Authenticated requests send the header `Authorization: Token <jwt>`
  (see `auth-headers` in `src/cljc/orcpub/dnd/e5/event_utils.cljc`).
- Tokens are Buddy JWS tokens signed with the `SIGNATURE` environment
  variable. The `check-auth` interceptor returns 401 for an expired or
  invalid token (routes.clj:145). The current client stores the token in
  app state and localStorage. Do the same, and treat any 401 as a redirect
  to login.

## Endpoint map

In the Auth column, "yes" means the request needs the `Authorization`
header. "Yes, owner" means the server also checks resource ownership and
returns 401 or 404 otherwise. "No" means the endpoint is public.

### Users and auth

| Method | Path | Auth | Handler (routes.clj) |
|--------|------|------|----------------------|
| POST | `/register` | no | `register` |
| POST | `/login` | no | `login` |
| GET | `/user` | yes | `get-user` |
| PUT | `/user` | yes | `update-user-preferences` |
| DELETE | `/user` | yes | `delete-user` |
| PUT | `/user/email` | yes | `request-email-change` |
| GET | `/check-email` | no | email availability |
| GET | `/check-username` | no | username availability |
| GET | `/verify` | no | email verification link |
| GET | `/re-verify` | no | resend verification |
| POST | `/reset-password` | yes (cookie) | `reset-password` |
| GET | `/send-password-reset` | no | `send-password-reset` |
| GET | `/unsubscribe` | no | email unsubscribe |
| POST | `/following/users/:user` | yes | `follow-user` |
| DELETE | `/following/users/:user` | yes | `unfollow-user` |

### Characters

| Method | Path | Auth | Handler |
|--------|------|------|---------|
| GET | `/dnd/5e/characters` | yes | `character-list` (full entities) |
| POST | `/dnd/5e/characters` | yes | `save-character` (create and update) |
| GET | `/dnd/5e/characters/:id` | no | `get-character` (public read) |
| DELETE | `/dnd/5e/characters/:id` | yes | `delete-character` |
| GET | `/dnd/5e/character-summaries` | yes | `character-summary-list` (list views) |
| POST | `/character.pdf` | no | `character-pdf-2` (see doc 06) |

Save semantics: `POST /dnd/5e/characters` upserts. A character with a
`:db/id` updates, and one without creates. The body is a strict entity
(the `::orcpub.entity.strict/entity` spec), and the server validates it
against the spec. An invalid payload gets a 400 with `spec/explain-data`
(routes.clj:946).

### Parties (`src/clj/orcpub/routes/party.clj`)

| Method | Path | Auth |
|--------|------|------|
| GET | `/dnd/5e/parties` | yes |
| POST | `/dnd/5e/parties` | yes |
| DELETE | `/dnd/5e/parties/:id` | yes, owner |
| PUT | `/dnd/5e/parties/:id/name` | yes, owner |
| POST | `/dnd/5e/parties/:id/characters` | yes, owner |
| DELETE | `/dnd/5e/parties/:id/characters/:character-id` | yes, owner |

### Folders (`src/clj/orcpub/routes/folder.clj`)

The folder endpoints have the same shape as the party endpoints:
`/dnd/5e/folders`, `/dnd/5e/folders/:id`, `/dnd/5e/folders/:id/name`,
`/dnd/5e/folders/:id/characters`, and
`/dnd/5e/folders/:id/characters/:character-id`.

### Homebrew magic items

| Method | Path | Auth | Notes |
|--------|------|------|-------|
| GET | `/dnd/5e/items` | yes | own item list |
| POST | `/dnd/5e/items` | yes | save (spec-validated) |
| GET | `/dnd/5e/items/:id` | no | public read |
| DELETE | `/dnd/5e/items/:id` | yes | |
| GET | `/dnd/5e/item-summaries` | yes | |

The other homebrew content types, such as classes, races, spells, and
monsters, are not stored through the API. They are kept client-side in
localStorage and in `.orcbrew` files. See doc 06.

## The strict entity format

This is the payload for saving and loading characters. The spec is
`src/cljc/orcpub/entity/strict.cljc`. The shape, with namespace prefixes
elided:

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

A selection has a `::key` and either one `::option` or many `::options`.
Each option has a `::key` and an optional `::int-value`, `::map-value`, or
nested `::selections`. Free-form fields are in `::values`. The engine
converts between this wire format and its internal format with
`to-strict` and `from-strict` (`src/cljc/orcpub/entity.cljc` and
`src/cljc/orcpub/dnd/e5/character.cljc`). The facade in doc 03 exposes
those, so the TypeScript app never builds this by hand.

## Deliverables

- [ ] The `web-ts/src/api/` client module, with typed functions for every endpoint above
- [ ] The Transit codec, with round-trip tests against the Phase 0 fixtures
- [ ] Auth and token handling: store the token, attach the header, and redirect to login on 401
- [ ] Optional: an OpenAPI-style Markdown doc generated from this table for contributors

## A deferred backend option

A server-side JSON content negotiation layer would remove the Transit
dependency, but it means Clojure work and a second format to keep
compatible. Skip it. `transit-js` is maintained, and the codec is about
100 lines.
