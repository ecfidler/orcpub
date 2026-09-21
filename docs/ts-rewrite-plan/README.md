# New App on the cljc Engine Library, with User-Level Content Compatibility — Plan Set 2

> **Status: active.** This is the plan being seriously considered for
> implementation. "The plan", unqualified, refers to this plan set.
>
> **Tracking:** the work is managed in Linear (workspace *Orc Alchemy*):
> project **PubDoor** for the engine package (Phase A, this fork; M0 is
> done) and project **Alchemy 5e** for the new app (Phase B, its own
> repository). Milestones, issues with acceptance criteria, the risk
> register and condensed copies of these documents live there; these files
> remain the technical reference Linear points back to.

A plan for building a **new web application** — new TypeScript/React UI and,
in time, its own backend — whose rules engine is the **existing Clojure(Script)
core compiled to JavaScript and used as a library** ("Option 2A"). A user of
the old Dungeon Master's Vault / orcpub app can carry their own data into the
new one with files they export themselves.

This plan set builds on Plan Set 1 (`docs/ts-frontend-plan/`), which describes
the compiled-engine facade and the UI scaffold. Where a Plan Set 1 document
applies verbatim it is referenced, not repeated. The difference between the
two sets: Plan Set 1 is a *frontend swap* on the existing backend; this set is
a *new product* that reuses only the engine.

## Premises

- **Engine = the `.cljc` core as a library.** `entity.cljc`, `template.cljc`,
  `modifiers.cljc`, `template_base.cljc`, and the `dnd/e5/*` content
  namespaces are compiled with shadow-cljs into an npm package behind a typed
  facade (Plan Set 1 doc 03). Nothing in the rules is rewritten. This is what
  makes content identity automatic: the same code produces the same keys,
  selections, and computed values.
- **Compatibility is for the user, not the operator.** The new app never
  talks to an old server, and its backend is designed on its own terms — it
  does not mirror orcpub's API, Transit wire format, or Datomic schema.
  Compatibility means: export `all-content.orcbrew` from the old app, import
  it here; get your characters out of an old instance, import them here.
- **No PDF export.** Dropped as a feature; `pdf_spec.cljc` and `pdf.clj` are
  not carried over.
- **SRD only**, as today: the compiled bundle contains only SRD 5.1 content
  (everything non-SRD in the source is reader-discarded or unreferenced);
  non-SRD content enters through homebrew files.

## What the investigation established (and why it matters here)

- The old app **exports homebrew but not characters**: `.orcbrew` export is
  built in (`events.cljs:3601-3737`), while characters live only on the
  server, reachable per character at a public URL. Character transfer needs
  a user-side tool; doc 03 provides one.
- The homebrew pipeline (parse → clean → validate → convert to template
  options) is ClojureScript that already exists (`import_validation.cljs`,
  `spell_subs.cljs`, `content_reconciliation.cljs`) — most of it pure
  functions, some of it re-frame subscriptions that must be lifted into
  plain functions for the library. Doc 02 and doc 04.
- The built-in races, backgrounds, and languages live in `spell_subs.cljs`
  (a `.cljs` file with re-frame subscriptions), not in `src/cljc`. The
  library must include and de-re-frame that namespace.
- `options.cljc` requires `re-frame` and a few modifiers read the global
  `app-db` (e.g. the Dueling fighting style, `options.cljc:1739-1758`). The
  library bundle therefore carries re-frame as a dependency, and the facade
  must provide whatever those reads expect. Doc 02 §"Wrinkles".
- The engine has known quirks a facade author must respect: laziness with no
  caching (hence the old 500 ms debounce), ordering via `array-map`,
  `available-selections` depending on the built character, and `ref`
  selections storing data at global paths. Doc 02 catalogs them.
- Ten forms of legacy drift exist in real saved characters and ten in real
  `.orcbrew` files; the old code handles most of them and the library
  inherits that handling. Doc 01 lists the exceptions.

## Plan documents

| Doc | Summary |
|-----|---------|
| [HANDOFF-phase-a.md](HANDOFF-phase-a.md) | **Start here for Phase A**: environment, repository state, the M1 sequence (as Linear issues), rules |
| [00-repo-strategy.md](00-repo-strategy.md) | Where the work happens: engine built and published from this fork, the app in its own repository |
| [01-compatibility-contract.md](01-compatibility-contract.md) | The three user-level contracts: homebrew (both ways), characters (old → new), content identity |
| [02-engine-library.md](02-engine-library.md) | What the compiled engine package must expose beyond Plan Set 1's facade; build scope; wrinkles; golden tests |
| [03-character-import-and-storage.md](03-character-import-and-storage.md) | Getting characters out of an old instance (exporter bookmarklet), importing them, the new app's native format |
| [04-homebrew.md](04-homebrew.md) | `.orcbrew` import/export through the library; validation, conflicts, storage; old bugs to fix |
| [05-app-and-backend.md](05-app-and-backend.md) | The application layer by reference to Plan Set 1, local-first mode, and the new backend |
| [06-milestones-and-risks.md](06-milestones-and-risks.md) | Milestone summary, decision record, definition of done (status and the risk register are in Linear) |

## Ground rules

- The work is split at the engine seam (doc 00): the engine library is
  built and published **from this fork** (`engine-js/`), and the new app is
  its own repository that consumes the published package and never imports
  Clojure. Engine patches are ordinary commits here.
- This repository is also the **reference and test oracle**: run it to
  capture fixtures and expected values (Plan Set 1 doc 01).
- Compatibility is enforced by tests, not intentions: every contract in
  doc 01 has a fixture set and a CI test.
