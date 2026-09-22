# Phase 0: Run the reference app

The goal is a running instance of the current app, the reference app, that
you can compare against at every step, plus captured examples of real API
traffic and saved characters.

## 0.1 Get it running

Follow the existing docs. They cover this phase entirely:

- `docs/GETTING-STARTED.md` for the devcontainer or a local setup.
- The Quick Start in `README.md`: `./scripts/dev-setup.sh`, then `./menu`.
- Or Docker: `docker-compose up` (see `docs/DOCKER.md`).

You need the Datomic transactor, the backend (port 8890 in dev, see
`src/clj/orcpub/system.clj`), and the figwheel ClojureScript build. Create
a test user, which `dev-setup.sh` does, and verify that you can log in,
build a character, save it, and export a PDF.

## 0.2 Capture reference traffic

With the app running, use the browser dev tools Network tab to record real
request and response pairs for each flow. Save them as fixture files,
suggested location `web-ts/fixtures/`. They become contract tests for the
new API client.

Capture at minimum:

1. `POST /login`: the request body and the response with the token and
   user data.
2. `GET /user`, with the `Authorization: Token <jwt>` header.
3. `GET /dnd/5e/character-summaries`: the character list payload.
4. `GET /dnd/5e/characters/:id`: a full saved character in the strict
   entity format. This is the single most important payload to understand.
5. `POST /dnd/5e/characters`: saving a character.
6. `POST /character.pdf`: the form post that produces the PDF.

Note the `Content-Type: application/transit+json` on API calls. Transit
looks like annotated JSON, for example
`["^ ","~:orcpub.entity.strict/selections",...]`. Do not parse it by hand.
`transit-js` decodes it (see doc 02).

## 0.3 Save reference characters

Build 3 to 5 characters in the current UI that cover the hard cases, and
export each as both saved-entity JSON (from the network capture) and PDF:

- A simple single-class martial character (Fighter), as the baseline.
- A prepared-spells caster (Cleric or Wizard), for the spell-slot and
  preparation logic.
- A multiclass character, for the hardest computed values.
- A character that uses homebrew content from an `.orcbrew` file.
- A character with custom equipment and magic items.

These become the golden files. The new frontend must render the same
computed values (AC, saves, skills, spell slots) for the same stored choice
tree. The engine facade's test suite (doc 03) asserts against them.

## 0.4 Learn the builder's behavior, not its code

Spend an hour clicking through the character builder and write down the
interaction model. You will reimplement the behavior, not the 339 KB
`views.cljs`. Note these things:

- The builder is a sequence of selection panes (race, class, abilities, and
  so on) driven by the template tree. Available options change as you
  pick.
- Selections can be nested: class, then subclass, then subclass features.
- The right-hand sheet preview updates live on every choice. The
  ClojureScript app debounces `entity/build` (see `built-character` in
  `src/cljs/orcpub/dnd/e5/subs.cljs`).
- Some values are free-form, such as the name and custom equipment, and
  are stored in the entity's `::values` map rather than the selections
  tree.

## Exit criteria

- [ ] The app runs locally, and you can log in, build, save, and export a PDF
- [ ] Fixture files are captured for the six flows above
- [ ] 3 to 5 golden characters are saved, as entity JSON and PDF
- [ ] One page of notes on the builder interaction model
