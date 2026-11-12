---
type: Reference
title: "Engine patch D8: scaled uses, and uses and features on homebrew feats"
description: What engine patch D8 in @pubdoor/dmv 0.4.0 changes, the key names of a scaled amount, and how the engine resolves it.
tags: [engine, patch, homebrew, uses]
status: stable
generated: { by: claude-code/agent, at: 2026-11-12T03:40:00Z }
sources:
  - id: patch-list
    resource: https://github.com/ecfidler/orc-alchemy/blob/main/docs/plan/02-engine-library.md
    title: "The engine as a library, section Patches to the engine source"
  - id: forms-spec
    resource: https://github.com/ecfidler/orc-alchemy/blob/main/docs/design/homebrew-forms.md
    title: "Homebrew forms spec, sections Uses and Engine 0.4.0"
---

# Summary

Patch D8 is the eighth patch to the engine source.[^patch-list] It is in
`@pubdoor/dmv` 0.4.0 (ORC-192). The homebrew forms of the new app need
it.[^forms-spec]

- A homebrew feature can have uses that scale with the character. The
  `:amount` of its `:frequency` is then a map, not a number.
- A homebrew feat can have `:frequency`, `:type` and `:traits`.

In this document, a feature is an entry in `:traits`. The engine code calls
it a trait.

Content of the SRD does not change. All fixtures that existed before the
patch are the same after it.

# Scaled amount

`:frequency` is `{:units <unit> :amount <amount>}`. The amount is a number
or a map. The map has one base, and the engine reads the bases in this
order: `:ability`, `:prof-bonus`, `:level`.

| Key | Value | Meaning |
|---|---|---|
| `:ability` | An ability key, with or without its namespace: `:wis` or `:orcpub.dnd.e5.character/wis` | Base: the modifier of that ability |
| `:prof-bonus` | `true` | Base: the proficiency bonus |
| `:level` | `true` | Base: a level. For a class or subclass feature, it is the level of that class. For any other feature, it is the character level |
| `:times` | A number. The default is 1 | Multiplies the level. The engine ignores it for the other bases |
| `:plus` | A number. The default is 0 | The engine adds it to the base |
| `:min` | A number. No default | The lowest result. The engine applies it after `:plus` |

The engine applies a minimum only when the map has `:min`. Without it, an
ability amount can be 0 or less. A map with no base gives the value of
`:plus`.

Examples, for a level 7 character with fighter level 6, a proficiency
bonus of 3, 10 Charisma and 8 Intelligence:

| Amount | Result |
|---|---|
| `{:ability :cha :min 1}` | 1 |
| `{:ability :orcpub.dnd.e5.character/int}` | -1 |
| `{:prof-bonus true :plus 2}` | 5 |
| `{:level true :times 5}` on a fighter subclass feature | 30 |
| `{:level true :times 2 :plus 1}` on a race feature | 15 |

# How the engine resolves it

The engine changes the map to a number when it evaluates the character.
Each reader of the built character gets a number.

- `scaled-amount` in `src/cljc/orcpub/dnd/e5/modifiers.cljc` calculates
  the number.
- `scaled-trait-cfg` is `trait-cfg` for a trait with a map amount. A
  modifier gets its dependencies from the `?` symbols in its body. Thus
  the body names `?ability-bonuses`, `?prof-bonus`, `?class-level` and
  `?total-levels`.
- `plugin-trait-cfg` uses `scaled-trait-cfg` only when the amount is a
  map. A trait with a number amount, or with no amount, gets the same
  modifier and the same dependencies as before the patch.
- `traits-modifiers` in `src/cljc/orcpub/dnd/e5/options.cljc` calls
  `plugin-trait-cfg`. It is the path for the traits of a race, subrace,
  background, class and subclass.

SRD content calculates its amounts in Clojure code, so it has no map
amount.

# Feats

Before the patch, a homebrew feat gave one trait from its `:name` and
`:description`, and ignored `:traits`.

- `feat-option-from-cfg` reads `:frequency`, `:type` and `:traits` from
  the feat.
- `feat-modifiers` puts `:frequency` and `:type` on the feat's own trait.
  A feat without them gets the same trait as before.
- The feat's `:traits` go through `traits-modifiers`. They are in
  `built.traits` after the feat's own trait. A level amount on a feat
  counts the character level.

# TypeScript types

- The output `Feature` has `type`.
- `ScaledAmount` and `PackFrequency` describe the pack side, as
  Transit-JSON.

# Fixtures and tests

- `fixtures/orcbrew/scaled-uses.orcbrew` has a race, a fighter subclass
  and two feats. `fixtures/characters/scaled-uses-fighter-6-barbarian-1`
  is the golden character.
- `test/cljc/orcpub/dnd/e5/modifiers_test.clj` tests `scaled-amount`.
- `engine-js/test/scaled-uses.test.ts` tests the cases of ORC-192.

# Limits

- A level table ("2 at level 1, 3 at level 3") is not possible.
- The old app does not resolve a map amount in its own views. It imports
  the pack with no error, but its sheet shows the amount incorrectly.

[^patch-list]: orc-alchemy `docs/plan/02-engine-library.md`, section
    "Patches to the engine source".
[^forms-spec]: orc-alchemy `docs/design/homebrew-forms.md`, sections
    "Uses" and "Engine 0.4.0".
