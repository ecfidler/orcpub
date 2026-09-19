# 09 — Milestones and Risks

## Milestone sequence

Sizes are relative, for a solo developer working part-time. The
engine-and-content track (M1–M4) is the critical path; app milestones
follow it by one step (doc 08).

| # | Milestone | Contents | Proof | Size |
|---|-----------|----------|-------|------|
| M0 | Reference & fixtures | Old app running; API captures; golden characters (4 levels × 12 classes, multiclass, homebrew, legacy quirks); built-value dumps from a REPL; `.orcbrew` community packs collected | Fixture repo with expected outputs committed | ~1 week |
| M1 | Data foundation | Tier-A JSON export (doc 03); EDN reader/printer; `parseStrict`/`toStrict` with all read tolerances (doc 05) | Round-trip tests on `character_test.clj` entities and captures; counts and cross-refs check | ~2 weeks |
| M2 | Engine core | Attribute registry (all ~110), effect kinds, dependency ordering, `evaluate`; **Fighter and Wizard** authored in the schema | Their golden characters match the old app at 4 levels; local-first viewer renders them | ~4–6 weeks |
| M3 | Full SRD | Remaining 10 classes + subclasses, races, Acolyte, Grappler, fighting styles, invocations, boons; magic items translated; equipment/AC/attacks complete | All golden characters match; key-namespace diff vs old template is empty (contract C2 proof) | ~6–10 weeks |
| M4 | Homebrew compat | Import pipeline with drift fixtures; orcbrew ↔ schema mapping; export validated against old spec; conflicts | Fixtures + community packs import losslessly; homebrew golden character matches | ~3–4 weeks |
| M5 | Drop-in frontend | Adapter + auth + list + sheet + PDF (via adapter) against a real old server | A user logs in to an old instance with the new frontend and sees/prints their characters | ~2–3 weeks |
| M6 | Builder | Full character builder on the new engine | Golden characters rebuilt from scratch in the new UI round-trip to identical strict entities, loadable in the old app | ~6–8 weeks |
| M7 | Local-first & browser PDF | IndexedDB backend, file import/export, `pdf-lib` filler | Playwright suite runs with no server | ~2–3 weeks |
| M8 | Homebrew UI & the rest | Schema-driven builders, my-content, conflicts UI; parties/folders/account; browse pages | Feature parity checklist | ~4–6 weeks |
| M9 | New backend (optional) | JSON API, auth, storage, `dmv migrate` | Migration from a dockerized old server verified end-to-end | ~4–6 weeks |

**Cut-over** to the new app as a replacement is credible after M6 (with the
adapter) or M7 (standalone). M9 is only needed if the operator wants to
retire the Clojure server.

## Where this plan set differs from Plan Set 1 — decision record

- Plan Set 1 (engine reuse) reaches a usable builder in roughly M1+M2 of its
  own sequence (~2 months). This plan set reaches the same point at M6
  (~6–9 months) because M2–M4 rebuild what the compiled engine gave for
  free. The payoff is a codebase with no Clojure anywhere, content as
  editable data, and a homebrew system whose format is the schema itself.
- The two are not exclusive: Plan Set 1's M1 (read-only client on the
  compiled engine) is a cheap way to get **M0's built-value dumps** without
  writing the REPL script — the compiled facade can emit them. Consider
  doing Plan Set 1 doc 03 steps 1–2 purely as tooling for this plan.

## Risk register

| Risk | Likelihood | Mitigation |
|---|---|---|
| **Schema can't express a class feature** without arbitrary code (the old engine's escape hatch) | Certain to happen several times | The effect-kind table is open: add a kind, not a general-purpose language. Author Fighter and Wizard first (M2) to shake this out before the long tail |
| **Subtle numeric divergence** (AC edge cases, multiclass slot rounding, the deliberate STR/DEX inversion for thrown/finesse, "first class only" saves) | High | Golden dumps at 4 levels per class + property tests for the math; treat every user-reported mismatch as a new fixture |
| **Key drift** — a new-app key differs from the old one, silently breaking old characters | Medium | The C2 diff script (doc 03 §Verification) in CI; never derive keys by a new rule — copy them |
| **Selection-structure drift** — same keys but different nesting, so old option paths don't resolve | Medium | Golden characters are *saved strict entities*, not just sheet values; loading them exercises the structure |
| **Ref-selection semantics** (summed min/max, global storage path) | Medium | Port `combine-ref-selections` faithfully; fixtures for languages, feats, invocations |
| **Legacy saves** with unnamespaced keys, map-form equipment, string XP | Medium | Normalizers are individually tested; keep a corpus of real old saves (ask users for anonymized exports) |
| **EDN edge cases** in the wild (encoding, exotic literals) | Medium | Own the parser; add every failing file as a fixture; progressive import never loses the valid parts |
| **Homebrew mechanics divergence** — a `:props` key or `:level-modifier` type behaves differently | Medium | The 30 + 12 vocabulary entries each get a micro-fixture: a one-feature homebrew race/class and the expected built values |
| **Scope explosion** into non-SRD content or new features during the port | High (it's tempting) | Rule: parity first. The excluded list in doc 03 is final until M8 |
| **Licensing** — the fillable PDFs, name tables, Vollkorn fonts | Low–medium | PDFs: same terms as the old repo; fonts: OFL; name tables: don't port (doc 03) |
| **Solo-developer fatigue over a 6–9 month critical path** | High | Every milestone produces a visible artifact (viewer → drop-in → builder); M2's two-class scope exists so the first sheet renders within ~2 months |

## Definition of done for the rewrite

- All five contracts in doc 01 have green test suites in CI.
- The key-namespace diff against the old template is empty.
- Every golden character (including legacy and homebrew cases) evaluates to
  the same sheet in both apps and round-trips through both apps' storage.
- A self-hoster can point the new frontend at their existing server and
  keep both frontends running.
