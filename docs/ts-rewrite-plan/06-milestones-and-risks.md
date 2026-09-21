# 06 — Milestones, Decisions and Risks

> **Status and sequencing live in Linear** (workspace *Orc Alchemy*).
> Milestones M0, M1 and the engine half of M3 are on project **PubDoor**;
> M2–M7 on project **Alchemy 5e**. Every milestone's issues carry their
> acceptance criteria; the *Risk register*, *Plan overview* and
> *Compatibility contract* are team documents there. This file keeps what
> an agent working in this repository needs without opening Linear: the
> sequence on one screen, the decision record, and the definition of done.

## Milestone sequence

| # | Milestone | Project | Proof |
|---|-----------|---------|-------|
| M0 | Reference & fixtures | PubDoor | Fixture set committed under `fixtures/` — **done** (PR #4) |
| M1 | Engine package 0.1 | PubDoor | Golden characters evaluate identically in TS tests; `@dmv/pubdoor` installable |
| M2 | Local-first viewer | Alchemy 5e | Read-only sheet from `evaluate`; Playwright smoke test |
| M3 | Homebrew engine path / in the app | both | Lossless and old-spec-acceptance tests green; homebrew golden characters match |
| M4 | Builder | Alchemy 5e | Golden characters rebuilt from scratch round-trip identically |
| M5 | Import tooling & homebrew UI | Alchemy 5e | A real user's `all-content.orcbrew` and bookmarklet bundle import cleanly |
| M6 | Backend | Alchemy 5e | Multi-device use |
| M7 | The rest | Alchemy 5e | Parity checklist |

A usable standalone app exists after **M4**; user migration is complete
after **M5**; multi-device after **M6**. Relative sizes are on the Linear
milestones.

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

## Definition of done

- All three contracts in doc 01 have green CI suites.
- Zero unresolved content keys across every golden character and fixture pack.
- A user of the old app can, with the published guide: export
  `all-content.orcbrew`, run the exporter bookmarklet, import both into the
  new app, and see every character evaluate to the same sheet values.

## Risks

The risk register is the *Risk register* document in Linear, extended with
what M0 found (JS-only semantics the JVM oracle had to shim; real exports
messier than the drift list). Review it at each milestone close and add a
row when a risk materializes in a new form.
