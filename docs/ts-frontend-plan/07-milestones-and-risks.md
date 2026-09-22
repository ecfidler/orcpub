# Milestones, sequencing, and risks

## Milestone sequence

Each milestone is independently useful and runs against the real backend.
The sizes are rough solo-developer estimates that assume part-time work.
Treat them as relative sizes, not commitments.

| # | Milestone | Contents | Size |
|---|-----------|----------|------|
| M0 | Reference captured | Phase 0 complete: app running, fixtures, golden characters | days |
| M1 | Read-only client | Scaffold, auth, character list, and read-only sheet (doc 04) | about 2 weeks |
| M2 | Engine proven | `@dmv/pubdoor` with golden tests. The M1 sheet uses it, so docs 03 and 04 overlap and M1 needs a minimal engine build early | about 2 to 3 weeks |
| M3 | PDF export | Doc 06 §PDF export | days |
| M4 | Builder core | 4.3 sub-milestones 1 to 3 (selections, abilities, classes) | the longest task, about 4 to 8 weeks |
| M5 | Builder complete | 4.3 sub-milestones 4 to 6 (equipment, spells, save and autosave) | about 3 to 4 weeks |
| M6 | Organization | Parties, folders, account pages | about 2 weeks |
| M7 | Content browsing | Spell, monster, and item pages | about 1 to 2 weeks |
| M8 | Homebrew | The minimum orcbrew import, then the builders in usage order | open-ended |

Cut-over is viable after M5, when users can build, save, view, and print
characters. M6 to M8 decide when the old frontend can be retired.

Note the M1 and M2 ordering. M1's read-only sheet already needs
`buildCharacter`, so the first slice of the engine package (facade steps 1
and 2 in doc 03) happens inside M1. M2 finishes the facade (selections and
mutations) and the test suite.

## Risk register

| Risk | Likelihood | Mitigation |
|------|-----------|------------|
| shadow-cljs build problems on first Clojure contact: deps, advanced compilation, and exports getting renamed | High, early | Timebox the scaffold to a few days. Put `^:export` on every facade function. Ask for help in the Clojurians Slack channel `#shadow-cljs`. This exact "ClojureScript library for a JS app" setup is common |
| The engine bundle size (about 2 MB of source data) hurts load time | Certain | An async chunk and a splash screen. The current app has the same weight. `:advanced` optimizations. Later, split the data namespaces into on-demand chunks |
| Boundary conversion (`clj->js` per keystroke) is too slow in the builder | Medium | Debounce, as the old app already does. If it is still slow, the opaque-session variant in doc 03 keeps the internal entity cached |
| Flattening `availableSelections` loses information the builder UI needs, such as help text, prerequisites, and ordering | Medium | Grow the facade shape as pages demand. The template nodes carry this metadata, so it is an exposure problem, not a logic problem |
| Strict-entity edge cases: legacy saved characters with quirks. See the normalization in the `character.cljc` `to-strict` and `from-strict` and the routes.clj:931 `xps` fixup | Medium | Always round-trip through the engine's converters, and never construct strict entities in TypeScript. Add every user-reported failure as a golden file |
| Scope creep into the backend | Medium | Rule: `src/clj` and `src/cljc` are read-only for this project, except for the facade in `engine-js/` |
| Solo-project fatigue on the builder (M4) | High | The sub-milestone split in doc 05 §4.3 exists for this. Each slice demos |
| Content licensing if the fork is public | Not rated | The data files include non-SRD content (SCAG, UA). The exposure is the same as upstream, but a public fork should keep the same takedown posture. This is not a code problem. Be aware of it |

## Working practices

- **Golden files are the contract.** Any engine or codec change must keep
  the golden character tests green. Add a golden file per bug fixed.
- **Old code is reference, not source.** When behavior is unclear, read
  the running app first and `character_builder.cljs` and `events.cljs`
  second. Never port ClojureScript code line by line. Reimplement the
  behavior.
- **Repo layout during the transition.** `web-ts/` and `engine-js/` sit
  beside the existing tree, with separate CI jobs. The Leiningen build is
  untouched.
- **Where to get unstuck on Clojure.** The facade is the only Clojure you
  write. Each facade function calls an existing function and converts the
  data. An LLM or a Clojurians Slack question handles each one. None
  requires understanding the engine's internals.
