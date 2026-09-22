# Phase 2: Package the rules engine for npm

The goal is `@dmv/pubdoor`, an npm package compiled from the existing
`.cljc` code with shadow-cljs that exposes the rules engine to TypeScript
behind a small typed facade. This is the only phase that involves writing
Clojure. The code is mechanical glue, about 300 to 500 lines, not game
logic.

## What the engine is

The generic machinery is in `src/cljc/orcpub/`:

- `template.cljc`. A template is a tree of selections and options: what
  can be chosen, and what each choice does through its attached modifiers.
- `entity.cljc`. An entity is a set of choices against a template. The key
  functions are `build` (entity.cljc:620), which folds choices into a built
  character, `available-selections`, which computes what can currently be
  picked, and `to-strict` and `from-strict`, which convert to and from the
  wire format.
- `modifiers.cljc` and `entity_spec.cljc`. How options change computed
  attributes.

The 5e instantiation is in `src/cljc/orcpub/dnd/e5/`:

- `template.cljc`. `template` (line 1565) assembles the full 5e character
  decision tree from the data namespaces.
- `character.cljc`. Accessors over a built character: `levels`,
  `total-levels`, `armor-class`, saves, skills, spells, and dozens more,
  plus 5e-specific `to-strict` and `from-strict` wrappers that normalize
  equipment and ability keys.
- The data namespaces, among them `classes.cljc`, `spells.cljc`,
  `monsters.cljc`, `magic_items.cljc`, and `options.cljc`. Content.

The current UI uses the engine in a pattern the facade must support (see
`src/cljs/orcpub/dnd/e5/subs.cljs:307`). It keeps a raw entity in state.
On every change it runs `entity/build`, debounced, to get the built
character and `entity/available-selections` to get the current choice
tree. Then it renders both.

## Package design

### Boundary contract

Plain JSON-safe JS goes in, and plain JSON-safe JS comes out. No
ClojureScript data structure ever crosses the boundary. Convert with
`clj->js` and `js->clj`, or with `cljs-bean` for performance, inside the
facade. Namespaced keywords become `"namespace/name"` strings, matching
the Transit codec convention from doc 02, so entities pass between the API
and the engine without translation.

### Facade API, the `.d.ts` you write by hand

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

- `selectOption` and `setValue` take the strict entity and return a new
  one. The TypeScript app treats entities as opaque immutable values, which
  fits React and Redux.
- Internally the facade converts the strict entity to the internal entity,
  applies the change, and converts back. If profiling shows this is too
  slow for keystroke-level updates, add an opaque-handle variant:
  `openSession(entity)` returns a stateful session with the internal
  representation cached. Start simple.
- The current UI debounces `entity/build`. Do the same in TypeScript, with
  `useDeferredValue` or a debounced selector.

### shadow-cljs setup

A new top-level directory, `engine-js/`, keeps `project.clj` untouched:

```
engine-js/
  shadow-cljs.edn        ; :npm-module or :esm target, release optimizations
  src/orcpub/engine.cljs ; the facade namespace (^:export fns)
  package.json           ; name @dmv/pubdoor, main/module entries
  types/index.d.ts       ; hand-written types (ships in the package)
  test/                  ; golden-file tests
```

`shadow-cljs.edn` points `:source-paths` at `["src" "../src/cljc"]` so
the facade compiles against the existing engine sources unmodified. Use
the `:esm` target with `:advanced` optimizations for release. `^:export`
metadata protects facade names from renaming. Expect a large bundle,
because the data files are about 2 MB of source. Load the engine as an
async chunk and show a splash screen while it loads, as the current app in
effect does.

Some `.cljc` files have `#?(:clj ...)` branches for spec requires. They
already compile for ClojureScript today. That is the whole point: this is
the same code the current frontend ships.

### Testing: golden files

For each Phase 0 golden character, `buildCharacter(savedEntity)` must
produce the same AC, HP, saves, skills, and spell slots that the reference
app shows. Write these as vitest tests in `engine-js/test/`. They are the
safety net for engine upgrades and for a future engine rewrite.

## Steps

1. Scaffold `engine-js/` with shadow-cljs. Get a hello-world export
   compiling and importable from a TypeScript file. This is the first
   Clojure hurdle, so budget a day or two.
2. Expose `buildCharacter` and a handful of `character.cljc` accessors.
   Make the first golden-file test pass.
3. Expose `availableSelections` with the flattened option-tree shape
   above. Study how `subs.cljs:217` calls `entity/available-selections` to
   learn the argument plumbing.
4. Expose `selectOption` and `setValue`. Verify that a scripted sequence
   of picks reproduces a golden character from scratch.
5. Expose `randomCharacter`, `parseOrcbrew`, and `withHomebrew`.
6. Set up `npm pack` or workspace linking so `web-ts/` consumes the
   package, and a CI job that rebuilds the package and runs the golden
   tests.

## Exit criteria

- [ ] `@dmv/pubdoor` builds reproducibly and imports from TypeScript with types
- [ ] Golden-file tests pass for all reference characters
- [ ] A scripted end-to-end run: an empty character, a sequence of picks, a strict entity, and acceptance by `POST /dnd/5e/characters`. This proves the engine output matches the server spec
