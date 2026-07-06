# Milestones, Sequencing, and Risks

## Milestone sequence

Each milestone is independently useful and runs against the real backend.
Rough solo-developer estimates, assuming part-time work; treat them as
relative sizes, not commitments.

| # | Milestone | Contents | Size |
|---|-----------|----------|------|
| M0 | Reference captured | Phase 0 complete: app running, fixtures, golden characters | days |
| M1 | Read-only client | Scaffold + auth + character list + read-only sheet (doc 04) | ~2 weeks |
| M2 | Engine proven | `@dmv/engine` with golden tests; sheet in M1 uses it (docs 03/04 overlap — M1 needs a minimal engine build early) | ~2–3 weeks |
| M3 | PDF export | doc 06 §PDF | days |
| M4 | Builder core | 4.3 sub-milestones 1–3 (selections, abilities, classes) | the long pole; ~4–8 weeks |
| M5 | Builder complete | 4.3 sub-milestones 4–6 (equipment, spells, save/autosave) | ~3–4 weeks |
| M6 | Organization | Parties, folders, account pages | ~2 weeks |
| M7 | Content browsing | Spell/monster/item pages | ~1–2 weeks |
| M8 | Homebrew | orcbrew import MVP + builders, by usage order | open-ended tail |

Cut-over is viable after **M5**: users can build, save, view, and print
characters. M6–M8 decide when the old frontend can actually be retired.

Note M1/M2 ordering: M1's read-only sheet already needs `buildCharacter`, so
the first slice of the engine package (facade steps 1–2 in doc 03) happens
inside M1. M2 is finishing the facade (selections, mutations) and the test
suite.

## Risk register

| Risk | Likelihood | Mitigation |
|------|-----------|------------|
| shadow-cljs build friction (first Clojure contact: deps, advanced compilation, exports getting renamed) | High, early | Timebox scaffold to a few days; `^:export` on every facade fn; ask for help in Clojurians Slack `#shadow-cljs` — this exact "cljs library for a JS app" setup is well-trodden |
| Engine bundle size (~2MB source data) hurts load time | Certain | Async chunk + splash (current app has the same weight); `:advanced` optimizations; later: split data namespaces into on-demand chunks |
| Boundary conversion (`clj->js` per keystroke) too slow in the builder | Medium | Debounce (the old app already does); if still slow, the opaque-session variant in doc 03 keeps the internal entity cached |
| `availableSelections` flattening loses information the builder UI needs (help text, prerequisites, ordering) | Medium | Grow the facade shape as pages demand; the template nodes carry this metadata — it's an exposure problem, not a logic problem |
| Strict-entity edge cases (legacy saved characters with quirks — see the normalization in `character.cljc` `to-strict`/`from-strict` and routes.clj:931 xps fixup) | Medium | Always round-trip through the engine's converters, never construct strict entities in TS; add every user-reported failure as a golden file |
| Scope creep into the backend | Medium | Rule: `src/clj` and `src/cljc` are read-only for this project (facade in `engine-js/` excepted) |
| Solo-project fatigue on the builder (M4) | High | The sub-milestone split in doc 05 §4.3 exists for this; each slice demos |
| Content licensing if the fork is public | — | The data files include non-SRD content (SCAG, UA); same exposure as upstream, but a public fork should keep the same takedown posture. Not a code problem; be aware |

## Working practices

- **Golden files are the contract.** Any engine or codec change must keep the
  golden character tests green. Add a golden file per bug fixed.
- **Old code is reference, not source.** When behavior is unclear, read the
  running app first, `character_builder.cljs`/`events.cljs` second. Never
  port cljs code line-by-line — reimplement the behavior.
- **Repo layout during transition**: `web-ts/` and `engine-js/` live beside
  the existing tree; separate CI jobs; the Leiningen build is untouched.
- **Where to get unstuck on Clojure**: the facade is the only Clojure you
  write. Each facade function is "call existing fn, convert data" — an LLM or
  a Clojurians-Slack question handles each one; none requires understanding
  the engine's internals.
