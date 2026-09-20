# 06 — Milestones and Risks

## Milestone sequence

Relative sizes for a solo developer working part-time. The compiled engine
removes the long content-authoring path a from-scratch engine would need;
the critical path is now facade glue, then UI.

| # | Milestone | Contents | Proof | Size |
|---|-----------|----------|-------|------|
| M0 | Reference & fixtures | *In this fork.* Old app running (Plan Set 1 doc 01); golden characters incl. homebrew and legacy cases; captured `GET /dnd/5e/characters/:id` payloads; community `.orcbrew` packs; built-value dumps | Fixture set committed under `fixtures/` | ~1 week |
| M1 | Engine library | *In this fork.* `engine-js/` shadow-cljs build; base facade (`evaluate`, mutations, `importCharacter`); `warlock_test` + `character_test` round-trips ported; `@dmv/pubdoor` published | Golden characters evaluate identically in TS tests; package installable | ~2–3 weeks |
| M2 | Local-first viewer | *App repo created.* App scaffold; import a captured character; read-only sheet from `evaluate`; IndexedDB storage; native export | First visible proof; Playwright smoke test | ~2 weeks |
| M3 | Homebrew engine path | *Engine half in the fork, UI in the app repo.* De-re-framed `buildTemplate`; `parseOrcbrew`/export via facade; both fixtures + community packs import; homebrew golden character matches | Lossless and old-spec-acceptance tests green | ~2–3 weeks |
| M4 | Builder | Full character builder (Plan Set 1 doc 05 §4.3 slices 1–6) | Golden characters rebuilt from scratch round-trip identically | ~6–8 weeks |
| M5 | Import tooling & homebrew UI | Exporter bookmarklet; import flow, log, conflicts, reconciliation; My Content; builders in usage order | A real user's `all-content.orcbrew` + bookmarklet bundle import cleanly | ~4–6 weeks |
| M6 | Backend | Accounts, character/homebrew documents, sharing, parties/folders, sync | Multi-device use | ~4–6 weeks |
| M7 | The rest | Browse pages (spells/monsters/items from JSON dumps), account pages, polish | Parity checklist | ~2–4 weeks |

A usable standalone app exists after **M4** (local-first, builder, import
from files); user migration is complete after **M5**; multi-device after
**M6**.

## Decision record

- **Why 2A over a from-scratch engine**: the investigation showed the rules
  live in ~110 lazy attributes plus ~100 modifier constructors with
  level-gating inside macro bodies, and the shipped content is 12 classes,
  9 races, 268 spells, 288 magic items and more. Re-expressing that as data
  and re-authoring it is a 6–9 month critical path on its own; compiling it
  is weeks. Content identity (contract C3) also becomes automatic.
- **Why build the engine in this fork rather than vendor it** (doc 00): the
  fork already has the Clojure toolchain and the running old app; the new
  app needs four small engine patches, which are ordinary commits here; and
  the app repo then never carries Leiningen, shadow-cljs, or the re-frame UI.
- **Why strict entity as the native format**: the engine consumes it, it's
  small, and it makes old-character import a normalization step.
- **Why no PDF**: dropped as a feature (user decision); removes `pdf_spec`,
  `pdf.clj`, the 28 templates, fonts, and image handling from scope.
- **Why local-first before backend**: it's the fastest path to something a
  user can evaluate, it makes the whole app testable without infrastructure,
  and it leaves the backend design unconstrained.

## Risk register

| Risk | Likelihood | Mitigation |
|---|---|---|
| shadow-cljs build friction on first contact (deps, `:advanced` renaming, exports) | High, early | Timebox; `^:export` everything in the facade; Clojurians `#shadow-cljs`; Plan Set 1 doc 03 steps 1–2 are the same hurdle and should be reused |
| re-frame coupling in `options.cljc` (app-db reads inside modifiers) | Certain | Audit call sites first; patch to read from the entity; keep re-frame as a dependency only for the namespace to load |
| De-re-framing `spell_subs.cljs` introduces a divergence from the old subscription chain | Medium | Compare `buildTemplate(fixture)` with REPL-captured old output; the chain is pure per-sub so the port is mechanical |
| Boundary conversion cost (`clj->js` of a built character on every keystroke) | Medium | One-pass extraction of the accessor list, memoized per entity value; opaque entity handle |
| Ordering loss when entities pass through JS objects | Medium | Selections as arrays everywhere; a test that a reordered entity builds differently (proving the test can catch it) |
| Legacy character quirks R5/R7 are *not* inherited | Certain | Implemented in `importCharacter`; fixtures per quirk; ask users for anonymized exports |
| Engine package and app drift apart (a facade change ships without the app pinning it, or fixtures in the app repo go stale) | Medium | Semver on `@dmv/pubdoor`, exact pins in the app repo; fixtures snapshot records the fork commit; golden tests run in both repos |
| Bundle size (~2 MB of content source) | Certain | `:advanced`; async chunk; exclude `random.cljc`, `templates/`, and serve monsters as JSON |
| Non-SRD leakage (name tables) into the published package | Low | Excluded from the shadow-cljs build by configuration |
| Scope creep into features the old app has but users don't need (Orcacle, combat tracker, newb builder) | Medium | Out of scope until M7 |
| Solo-developer fatigue | High | Every milestone yields a visible artifact; M2 renders a sheet within ~5 weeks of starting |

## Definition of done

- All three contracts in doc 01 have green CI suites.
- Zero unresolved content keys across every golden character and fixture pack.
- A user of the old app can, with the published guide: export
  `all-content.orcbrew`, run the exporter bookmarklet, import both into the
  new app, and see every character evaluate to the same sheet values.
