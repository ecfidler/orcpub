# Phase 0 — Run the Reference App

Goal: a running instance of the current app you can compare against at every
step, plus captured examples of real API traffic and saved characters.

## 0.1 Get it running

Follow the existing docs — this phase is entirely covered by them:

- `docs/GETTING-STARTED.md` — devcontainer or local setup
- `README.md` Quick Start — `./scripts/dev-setup.sh` then `./menu`
- Or Docker: `docker-compose up` (see `docs/DOCKER.md`)

You need: the Datomic transactor, the backend (port 8890 in dev, see
`src/clj/orcpub/system.clj`), and the figwheel CLJS build. Create a test user
(`dev-setup.sh` does this) and verify you can log in, build a character, save
it, and export a PDF.

## 0.2 Capture reference traffic

With the app running, use the browser dev tools Network tab to record real
request/response pairs for each flow. Save them as fixture files (suggested:
`web-ts/fixtures/`) — they become contract tests for the new API client.

Capture at minimum:

1. `POST /login` — request body and the token + user-data response
2. `GET /user` — with the `Authorization: Token <jwt>` header
3. `GET /dnd/5e/character-summaries` — the character list payload
4. `GET /dnd/5e/characters/:id` — a full saved character (the **strict
   entity** format; this is the single most important payload to understand)
5. `POST /dnd/5e/characters` — saving a character
6. `POST /character.pdf` — the form post that produces the PDF

Note the `Content-Type: application/transit+json` on API calls. Transit looks
like annotated JSON (`["^ ","~:orcpub.entity.strict/selections",...]`); don't
hand-parse it — `transit-js` decodes it (see doc 04).

## 0.3 Save reference characters

Build 3–5 characters in the current UI covering the hard cases, and export
each as both saved-entity JSON (from the network capture) and PDF:

- A simple single-class martial (Fighter) — baseline
- A prepared-spells caster (Cleric or Wizard) — spell-slot/preparation logic
- A multiclass character — the trickiest computed values
- A character using homebrew content from an `.orcbrew` file
- A character with custom equipment and magic items

These become golden files: the new frontend must render the same computed
values (AC, saves, skills, spell slots) for the same stored choice tree. The
engine facade's test suite (doc 03) asserts against them.

## 0.4 Learn the builder's behavior (not its code)

Spend an hour clicking through the character builder and write down the
interaction model — you'll re-implement the *behavior*, not the 339KB
`views.cljs`. Key things to note:

- The builder is a sequence of selection panes (race → class → abilities →
  ...) driven by the template tree; available options change as you pick.
- Selections can be nested (class → subclass → subclass features).
- The right-hand sheet preview updates live on every choice (the cljs app
  debounces `entity/build` — see `built-character` in
  `src/cljs/orcpub/dnd/e5/subs.cljs`).
- Some values are free-form (name, custom equipment) and live in the entity's
  `::values` map rather than the selections tree.

## Exit criteria

- [ ] App runs locally; you can log in, build, save, and export PDF
- [ ] Fixture files captured for the six flows above
- [ ] 3–5 golden characters saved (entity JSON + PDF)
- [ ] One-page notes on builder interaction model
