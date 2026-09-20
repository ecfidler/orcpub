# Phase 2 — The Rules Engine as an npm Package

Goal: `@dmv/pubdoor` — an npm package, compiled from the existing `.cljc` code
with shadow-cljs, exposing the rules engine to TypeScript behind a small typed
facade. This is the only phase that involves writing Clojure. The code is
mechanical glue (~300–500 lines), not game logic.

## What the engine is

The generic machinery (`src/cljc/orcpub/`):

- `template.cljc` — a **template** is a tree of selections and options: what
  *can* be chosen and what each choice does (via attached modifiers)
- `entity.cljc` — an **entity** is a set of choices against a template.
  Key functions: `build` (entity.cljc:620) folds choices into a built
  character; `available-selections` computes what can currently be picked;
  `to-strict`/`from-strict` convert to/from the wire format
- `modifiers.cljc`, `entity_spec.cljc` — how options mutate computed attributes

The 5e instantiation (`src/cljc/orcpub/dnd/e5/`):

- `template.cljc` — `template` (line 1565) assembles the full 5e character
  decision tree from the data namespaces
- `character.cljc` — accessors over a built character: `levels`,
  `total-levels`, and dozens more (`armor-class`, saves, skills, spells...)
  plus 5e-specific `to-strict`/`from-strict` wrappers that normalize equipment
  and ability keys
- the data namespaces (`classes.cljc`, `spells.cljc`, `monsters.cljc`,
  `magic_items.cljc`, `options.cljc`, ...) — content

How the current UI uses it (the pattern the facade must support — see
`src/cljs/orcpub/dnd/e5/subs.cljs:307`): keep a raw entity in state; on every
change run `entity/build` (debounced) to get the built character and
`entity/available-selections` to get the current choice tree; render both.

## Package design

### Boundary contract

Plain JSON-safe JS in, plain JSON-safe JS out. No ClojureScript data
structures ever cross the boundary — convert with `clj->js`/`js->clj` (or
`cljs-bean` for performance) inside the facade. Namespaced keywords become
`"namespace/name"` strings, matching the Transit codec convention from doc 02
so entities flow between API and engine without translation.

### Facade API (the `.d.ts` you write by hand)

```ts
// A character entity in strict wire format (what the API stores)
export type StrictEntity = object;

export interface BuiltCharacter {
  // computed sheet values, extracted via character.cljc accessors
  abilities: Record<string, number>;
  armorClass: number;
  maxHitPoints: number;
  skills: Record<string, number>;
  saveBonuses: Record<string, number>;
  spellSlots: Record<number, number>;
  levels: Record<string, { classLevel: number }>;
  // ... grow this as pages need values; keep it flat and serializable
}

export interface SelectionOption {
  key: string;
  name: string;
  selected: boolean;
  disabled?: boolean;
  help?: string;
}

export interface AvailableSelection {
  key: string;
  path: string[];          // where in the tree this selection lives
  name: string;
  min: number; max: number | null;
  options: SelectionOption[];
}

export function emptyCharacter(): StrictEntity;
export function buildCharacter(entity: StrictEntity): BuiltCharacter;
export function availableSelections(entity: StrictEntity): AvailableSelection[];
export function selectOption(entity: StrictEntity, path: string[], optionKey: string): StrictEntity;
export function setValue(entity: StrictEntity, key: string, value: unknown): StrictEntity;
export function randomCharacter(): StrictEntity;   // character/random.cljc
// homebrew (doc 06):
export function parseOrcbrew(edn: string): object;
export function withHomebrew(content: object): void; // extends the template
```

Design notes:

- `selectOption`/`setValue` mutate the *strict entity* and return a new one —
  the TS app treats entities as opaque immutable values, which fits
  React/Redux naturally.
- Internally the facade converts strict → internal entity, applies the
  change, converts back. If profiling shows this is too slow for keystroke-
  level updates, add an opaque-handle variant (`openSession(entity)` returning
  a stateful session with the internal representation cached). Start simple.
- The current UI debounces `entity/build`; do the same in TS
  (`useDeferredValue` or a debounced selector).

### shadow-cljs setup

New top-level dir `engine-js/` (keeps `project.clj` untouched):

```
engine-js/
  shadow-cljs.edn        ; :npm-module or :esm target, release optimizations
  src/orcpub/engine.cljs ; the facade namespace (^:export fns)
  package.json           ; name @dmv/pubdoor, main/module entries
  types/index.d.ts       ; hand-written types (ships in the package)
  test/                  ; golden-file tests
```

`shadow-cljs.edn` points `:source-paths` at `["src" "../src/cljc"]` so the
facade compiles against the existing engine sources unmodified. Use the
`:esm` target with `:advanced` optimizations for release; `^:export` metadata
protects facade names from renaming. Expect a large bundle (the data files are
~2MB of source) — load the engine as an async chunk and show a splash while it
loads, exactly like the current app effectively does.

Note: some `.cljc` files have `#?(:clj ...)` branches (spec requires); these
already compile for cljs today — the whole point is that this is the same code
the current frontend ships.

### Testing: golden files

For each Phase 0 golden character: `buildCharacter(savedEntity)` must produce
the same AC / HP / saves / skills / spell slots the reference app shows.
Write these as vitest tests in `engine-js/test/`. This is the safety net for
engine upgrades and for a future engine rewrite.

## Steps

1. Scaffold `engine-js/` with shadow-cljs; get a hello-world export compiling
   and importable from a TS file. (First Clojure hurdle; budget a day or two.)
2. Expose `buildCharacter` + a handful of `character.cljc` accessors; make the
   first golden-file test pass.
3. Expose `availableSelections` with the flattened option-tree shape above.
   Study how `subs.cljs:217` calls `entity/available-selections` for the
   argument plumbing.
4. Expose `selectOption`/`setValue`; verify a scripted sequence of picks
   reproduces a golden character from scratch.
5. Expose `randomCharacter`, `parseOrcbrew`, `withHomebrew`.
6. Wire `npm pack` / workspace linking so `web-ts/` consumes it; set up a CI
   job that rebuilds the package and runs golden tests.

## Exit criteria

- [ ] `@dmv/pubdoor` builds reproducibly; importable from TS with types
- [ ] Golden-file tests pass for all reference characters
- [ ] A scripted end-to-end: empty → picks → strict entity → accepted by
      `POST /dnd/5e/characters` (proves engine output matches server spec)
