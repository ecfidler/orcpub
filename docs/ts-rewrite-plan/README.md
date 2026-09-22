# Plan Set 2: a new app on the cljc engine library, with user-level content compatibility

> Status: active. This is the plan under consideration for implementation.
> "The plan", unqualified, means this plan set.
>
> Tracking: the work is managed in Linear, workspace *Orc Alchemy*. Project
> PubDoor holds the engine package (Phase A, built in this fork). M0 is
> done. Project Alchemy 5e holds the new app (Phase B, its own repository).
> Milestones, issues with acceptance criteria, the risk register, and
> condensed copies of these documents are there. These files remain the
> technical reference that Linear points back to.

This plan builds a new web application: a new TypeScript and React UI and,
in time, its own backend. Its rules engine is the existing `.cljc` core,
compiled to JavaScript and used as a library. The investigation called this
approach "Option 2A". A user of the old app, Dungeon Master's Vault
(orcpub), can carry their own data into the new one with files they export
themselves.

This plan set builds on Plan Set 1 (`docs/ts-frontend-plan/`), which
describes the compiled-engine facade and the UI scaffold. Where a Plan Set 1
document applies verbatim, this set references it rather than repeating it.
The difference between the two sets is scope. Plan Set 1 replaces the
frontend on the existing backend. This set builds a new product that reuses
only the engine.

## Premises

- **The engine is the `.cljc` core, used as a library.** shadow-cljs
  compiles `entity.cljc`, `template.cljc`, `modifiers.cljc`,
  `template_base.cljc`, and the `dnd/e5/*` content namespaces into an npm
  package behind a typed facade (Plan Set 1 doc 03). Nothing in the rules is
  rewritten. Content identity is therefore automatic: the same code produces
  the same keys, selections, and computed values.
- **Compatibility is for the user, not the operator.** The new app never
  talks to an old server. Its backend is designed independently and does not
  mirror orcpub's API, Transit wire format, or Datomic schema. Compatibility
  means that a user can export `all-content.orcbrew` from the old app and
  import it here, and can get their characters out of an old instance and
  import them here.
- **No PDF export.** PDF export is dropped as a feature. `pdf_spec.cljc` and
  `pdf.clj` are not carried over.
- **SRD only, as today.** The compiled bundle contains only SRD 5.1 content.
  Everything non-SRD in the source is either discarded by the `#_` reader
  macro or unreferenced. Non-SRD content enters only through homebrew files.

## What the investigation established, and why it matters here

- The old app exports homebrew but not characters. `.orcbrew` export is
  built in (`events.cljs:3601-3737`). Characters exist only on the server,
  where each one is reachable at a public URL. Character transfer therefore
  needs a user-side tool, which doc 03 provides.
- The homebrew pipeline, which parses, cleans, validates, and converts a
  file to template options, already exists as ClojureScript in
  `import_validation.cljs`, `spell_subs.cljs`, and
  `content_reconciliation.cljs`. Most of it is pure functions. Some of it is
  re-frame subscriptions that the library must rewrite as plain functions.
  See doc 02 and doc 04.
- The built-in races, backgrounds, and languages are defined in
  `spell_subs.cljs`, a `.cljs` file with re-frame subscriptions, not in
  `src/cljc`. The library must include that namespace and rewrite its
  subscriptions as plain functions (doc 02 §De-re-framing).
- `options.cljc` requires `re-frame`, and a few modifiers read the global
  `app-db`, for example the Dueling fighting style
  (`options.cljc:1739-1758`). The library bundle therefore carries re-frame
  as a dependency, and the facade must provide whatever those reads expect.
  See doc 02 §Wrinkles.
- The engine has known behaviors a facade author must respect. Doc 02
  §Wrinkles lists them: lazy attributes with no caching (the reason for the
  old 500 ms debounce), ordering through `array-map`, the dependency of
  `available-selections` on the built character, and `ref` selections that
  store data at global paths.
- Real saved characters show ten forms of legacy drift, and real `.orcbrew`
  files show another ten. The old code handles most of them, and the library
  inherits that handling. Doc 01 lists the exceptions.

## Plan documents

| Doc | Summary |
|-----|---------|
| [HANDOFF-phase-a.md](HANDOFF-phase-a.md) | Start here for Phase A: environment, repository state, the M1 sequence as Linear issues, and the rules |
| [00-repo-strategy.md](00-repo-strategy.md) | Where the work happens: the engine is built and published from this fork, and the app is its own repository |
| [01-compatibility-contract.md](01-compatibility-contract.md) | The three user-level contracts: homebrew in both directions, characters from the old app to the new, and content identity |
| [02-engine-library.md](02-engine-library.md) | What the compiled engine package must expose beyond Plan Set 1's facade, the build scope, the wrinkles, and the golden tests |
| [03-character-import-and-storage.md](03-character-import-and-storage.md) | Getting characters out of an old instance with the exporter bookmarklet, importing them, and the new app's native format |
| [04-homebrew.md](04-homebrew.md) | `.orcbrew` import and export through the library: validation, conflicts, storage, and old bugs to fix |
| [05-app-and-backend.md](05-app-and-backend.md) | The application layer by reference to Plan Set 1, local-first mode, and the new backend |
| [06-milestones-and-risks.md](06-milestones-and-risks.md) | Milestone summary, decision record, and definition of done. Status and the risk register are in Linear |

## Ground rules

- The work is split at the engine package boundary (doc 00). The engine
  library is built and published from this fork, under `engine-js/`. The new
  app is its own repository, consumes the published package, and never
  imports Clojure. Engine patches are ordinary commits here.
- This repository is also the reference and test oracle. Run it to capture
  fixtures and expected values (Plan Set 1 doc 01).
- Tests, not intentions, enforce compatibility. Every contract in doc 01 has
  a fixture set and a CI test.
