# @pubdoor/dmv

The 5e rules engine from Dungeon Master's Vault, compiled from
ClojureScript to one ES module. It builds a character from its saved
choices and returns the computed sheet: ability scores, armor class, hit
points, spells, and the selections still open. It evaluates the 2014 rules
and contains SRD 5.1 content only.

## Install

```sh
npm install @pubdoor/dmv@0.1.0
```

The package is public on npm, so you need no token and no `.npmrc`.

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
`fixtures/README.md` in the repository describes the fields of `built`.

## Exported functions

`types/index.d.ts` holds the full signatures and the types.

- `evaluate(entity, options?)` builds a character and returns `{ built, selections }`.
- `importCharacter(entity)` migrates a character saved by the old app.
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

Each mutation returns a new entity. It throws with the reason when the old
builder would refuse the same change.

## The rules option

`evaluate`, `autofill`, and every mutation with an `options` argument take
`{ rules }`. The only value is `"2014"`, which is the default. Any other
value throws.

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
npm run build   # writes dist/pubdoor.js
npm test        # tsc --noEmit, then the vitest golden tests against ../fixtures
```

## Publish a version

Publishing is manual. To publish, log in with `npm login` as a member of
the `pubdoor` npm org.

1. Set `version` in `package.json` and run `npm install --package-lock-only`.
2. Commit the change.
3. From `engine-js/`, run `npm publish`. The `prepublishOnly` script builds and
   tests the package first.
4. Tag the commit as `pubdoor-v<version>` and push the tag. For 0.1.0:

   ```sh
   git tag pubdoor-v0.1.0
   git push origin pubdoor-v0.1.0
   ```

## License

EPL-2.0, the same license as the rest of the repository.
