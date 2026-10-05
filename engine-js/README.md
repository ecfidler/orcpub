# @pubdoor/dmv

This package is the 5e rules engine from Dungeon Master's Vault, compiled
from ClojureScript to one ES module. It builds a character from its saved
choices and returns the computed sheet: ability scores, armor class, hit
points, spells, and the selections still open. It evaluates the 2014 rules
and contains SRD 5.1 content only.

## Install

```sh
npm install --save-exact @pubdoor/dmv
```

The package is public on npm, so you need no token and no `.npmrc`. Pin an
exact version, as `--save-exact` does. See [Versions](#versions).

The package is ESM only. Load it with `import` from an ES module: a `.mjs`
file, or a project with `"type": "module"`. CommonJS `require` is not
supported.

## Evaluate a character

```js
import { emptyCharacter, evaluate } from "@pubdoor/dmv";

const { built, selections } = evaluate(emptyCharacter());

console.log(built.classes);           // ["barbarian"]
console.log(built["armor-class"]);    // 12
console.log(built["max-hit-points"]); // 13
console.log(selections.length);       // 17
```

A character is a strict entity in Transit-JSON, the format the old app
saves. Store it as it is, and change it only with the functions below.
[`fixtures/README.md`](https://github.com/ecfidler/orcpub/blob/pubdoor/fixtures/README.md)
describes the fields of `built`.

## Exported functions

The package exports the functions below. `types/index.d.ts` holds their
full signatures and the types.

- `evaluate(entity, options?)` builds a character and returns `{ built, selections }`.
- `importCharacter(entity)` migrates a character saved by the old app and returns `{ entity, legacyId }`. Pass `.entity` to the other functions.
- `exportCharacter(entity)` serializes a character as the old app saves it.
- `emptyCharacter()` returns the builder's new character, a level 1 barbarian.
- `select(entity, path, optionKey, options?)` selects an option.
- `deselect(entity, path, optionKey, options?)` removes a selected option.
- `setValue(entity, key, value)` sets a character value, such as `"character-name"`.
- `setField(entity, path, value, options?)` sets an option's value, such as hit points or an ability score.
- `addLevel(entity, classKey, options?)` adds a level to a class.
- `removeLevel(entity, classKey, options?)` removes the highest level of a class.
- `setClass(entity, index, classKey, options?)` replaces a class with another at level 1.
- `addClass(entity, classKey, options?)` adds a class at level 1.
- `removeClass(entity, classKey, options?)` removes a class.
- `addStartingEquipment(entity, backgroundKey, options?)` replaces the background starting equipment.
- `addInventoryItem(entity, selectionKey, itemKey)` adds an item to an inventory selection.
- `removeInventoryItem(entity, selectionKey, itemKey)` removes an item from an inventory selection.
- `increaseAbility(entity, path, abilityKey, options?)` adds one to an ability in an ability score improvement.
- `decreaseAbility(entity, path, abilityKey, options?)` removes one pick of an ability from an ability score improvement.
- `autofill(entity, options?)` fills a character at random, as the builder's random character button does.
- `parseOrcbrew(text, options?)` reads an `.orcbrew` file through the old importer and returns `{ success, data, log, conflicts, skipped }`. `data` is homebrew, the packs as verbose Transit-JSON keyed by pack name: `options.existing` with the file merged in, as the old app merged it. Two forms that the old engine ignored are fixed on import, each fix logged in `log.changes`: an ability key such as `:con` becomes `:orcpub.dnd.e5.character/con` in a race's `abilities`, a feat's `ability-increases` and `prereqs`, a class's `profs` `save`, a class's or subclass's `spellcasting` `ability`, and a `:spell` level-modifier's `ability` (`normalized-ability-key`). A `skill-options` or `multiclass-skill-options` without `choose` gets `choose` 1, the builder's default (`defaulted-choose`).
- `validateForExport(homebrew, options?)` checks packs before export, as the old app's export buttons did, and checks each item's key, pack, and nils. It returns `{ valid, packs, filled }`. `filled` is the homebrew with those items repaired and with placeholders for missing required fields, which the old app's "export anyway" wrote.
- `orcbrewToEdn(homebrew, options?)` returns `.orcbrew` text: all packs as the old app's `all-content.orcbrew`, or with `options.pack` one pack alone. `options.pretty` pretty-prints it.
- `renameKey(homebrew, { pack, contentType, from, to })` renames one item's key in one pack, as the old conflict modal's rename did, and returns the new homebrew. It also rewrites the pack's references to the item: a class's subclasses, the spells that list it, and the classes and subclasses that use its spell list; a race's subraces and the feats that require it; a selection's `level-selections`. Pass a `KeyConflict`'s `content-type`, `key`, and suggested key. Saved characters keep the old key, so `reconcileMissingContent` reports them for a remap.
- `reconcileMissingContent(entity, homebrew?)` checks every option key in a character against the template for homebrew, or for the SRD alone, and returns `{ hasMissing, items, unresolvedOptions }`. `items` lists each race, subrace, background, class, subclass, or feat that does not resolve, with the old app's suggestions from the loaded packs. `unresolvedOptions` lists any other choice that does not resolve under a parent that does. The entity is not changed.
- `buildTemplate(homebrew?)` builds the template for homebrew, or for the SRD alone, and returns `{ summary, shape, content }`: the top-level selections and their option keys, the template's structure without functions, and the content lists, such as races and classes.
- `keys.selectionKeys(homebrew?)` and `keys.optionKeys(homebrew?)` return every selection key and every option key in the template for homebrew, or for the SRD alone, once each and sorted. Saved characters and `.orcbrew` files refer to content by these keys.

Each mutation returns a new entity. It throws with the reason when the old
builder would refuse the same change.

## Content lists

The build writes the SRD content as JSON files next to the engine, so that
pages such as a spell list can load content without the engine. Import a
list by its file name:

```js
const { default: spells } = await import("@pubdoor/dmv/content/spells.json", {
  with: { type: "json" },
});

console.log(spells.length); // 319
```

| File | Items | What each item holds |
|---|---|---|
| `classes.json` | 12 | Key and name. The choices are in `buildTemplate().shape` |
| `races.json` | 9 | The race, its subraces, and its traits |
| `backgrounds.json` | 1 | The background |
| `feats.json` | 1 | Key and name |
| `languages.json` | 16 | Key and name |
| `spells.json` | 319 | The spell |
| `monsters.json` | 317 | The monster |
| `magic-items.json` | 805 | The magic item. A weapon or armor item becomes one item per base weapon or armor, as the builder lists them |
| `weapons.json` | 40 | The weapon |
| `ammunition.json` | 5 | The ammunition |
| `armor.json` | 14 | The armor or shield |
| `equipment.json` | 162 | The gear, tool, pack, mount, or vehicle |
| `treasure.json` | 11 | The coin or gem |

Keywords are `"ns/name"` strings, as in `built`. Functions and the
engine's `modifiers` and `selections` are left out. The lists hold the SRD
only: homebrew content is in the homebrew itself.

The `./content/*.json` export has no types condition, so TypeScript does
not apply the package's types to these imports. To use the types in
`types/index.d.ts`, cast each import to its `ContentLists` entry:

```ts
import type { ContentLists } from "@pubdoor/dmv";

const spells = (await import("@pubdoor/dmv/content/spells.json", { with: { type: "json" } }))
  .default as ContentLists["spells"];
```

## Load the engine as an async chunk

`dist/pubdoor.js` is large, so load it with a dynamic `import()` behind a
splash screen, not with a static import in the app's entry chunk:

```js
const engine = await import("@pubdoor/dmv");
```

A bundler such as Vite then puts the engine in its own chunk, which the
browser caches between visits. Pages that need only content lists import
the JSON files and never load the engine.

## Bundle size

On the `:advanced` release build, `dist/pubdoor.js` is about 1.5 MiB, or
about 400 KiB gzipped. The 13 files in `dist/content/` add about 1.5 MiB,
which the engine never loads. `monsters.json` is about 470 KiB of that.
Before ORC-42, `dist/pubdoor.js` was about 2 MiB and held the monsters.
`test/bundle.test.ts` logs the exact sizes in CI and fails if
`dist/pubdoor.js` grows past its budget.

The build leaves these namespaces out of `dist/pubdoor.js`:
`character/random.cljc`, everything under `templates/`, `pdf_spec.cljc`,
`char_decision_tree.cljc`, and the monster list in `monsters.cljc`.
`test/bundle.test.ts` checks each one.

## The rules option

`evaluate`, `autofill`, and every mutation with an `options` argument take
`{ rules }`. The only value is `"2014"`, which is the default. Any other
value throws.

## The homebrew option

`evaluate`, `autofill`, and the mutations also take `{ homebrew }`: the
loaded `.orcbrew` packs, keyed by pack name, as verbose Transit-JSON. To
load a file, pass the current homebrew to `parseOrcbrew` as
`options.existing` and replace it with the result's `data`. `parseOrcbrew`
merges the file in as the old app did, so do not merge packs yourself.
Without the option, characters build against the SRD only. The engine
rebuilds its template only when the homebrew changes.

## Versions

The package follows semver. Every engine patch or facade addition bumps the
version. Pin an exact version, because a new version can change sheet
values.

## Build and test from source

The build needs Node and a JDK, because shadow-cljs runs on the JVM. JDK 21
works. The package compiles the engine in place from `../src/cljc` and
`../src/cljs`, so build it inside a clone of the repository.

```sh
cd engine-js
npm ci
npm run build   # writes dist/pubdoor.js and dist/content/*.json
npm test        # tsc --noEmit, then the vitest golden tests against ../fixtures
```

`test/private-export.test.ts` imports a real `all-content.orcbrew` and
compares the result with its committed summary. It runs only when the
export is at `fixtures/orcbrew/private/all-content3.orcbrew`, which git
ignores. See `fixtures/README.md`, *Private exports*.

`test/content-identity.test.ts` is the contract C3 check. It fails when
key derivation changes: when a fixture character's option keys stop
resolving, when a fixture pack refers to a class, race, or selection that
does not exist, or when the SRD template's keys differ from the baseline.

## Publish a version

Publishing is manual. You must be a member of the `pubdoor` npm org.

1. Set `version` in `package.json`.
2. Run `npm install --package-lock-only`.
3. Commit the change and merge it to `pubdoor`.
4. Check out the merged commit on `pubdoor`.
5. Log in with `npm login`.
6. From `engine-js/`, run `npm publish`. The `prepublishOnly` script builds
   and tests the package first. If your account uses two-factor
   authentication, npm asks for a one-time password.
7. Tag the same commit and push the tag:

   ```sh
   git tag pubdoor-v<version>
   git push origin pubdoor-v<version>
   ```

## License

EPL-2.0, the same license as the rest of the repository. `dist/pubdoor.js`
bundles third-party libraries, which `THIRD-PARTY-NOTICES` lists with their
licenses.
