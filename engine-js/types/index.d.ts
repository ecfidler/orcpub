/**
 * A strict entity (orcpub.entity.strict/entity) as Transit-JSON: the text,
 * or the value JSON.parse returns for it. The fixtures under
 * fixtures/characters/*.strict.json use this format.
 */
export type StrictEntity = string | object;

/** The rules edition. 0.1 supports only "2014". */
export type Rules = "2014";

export interface EvaluateOptions {
  /** Defaults to "2014". Any other value throws. */
  rules?: Rules;
  /** Accepted and ignored until buildTemplate (ORC-27). */
  homebrew?: unknown;
}

/**
 * The built character as plain JSON data, keyed by the old app's
 * character-subs names. See fixtures/README.md §expected.json.
 * ORC-24 replaces this with typed fields.
 */
export type BuiltCharacter = Record<string, unknown>;

/** One entry of entity/available-selections, flattened. */
export interface Selection {
  key: string;
  name: string;
  path: unknown[];
  /** The path where the selection's data is stored. Differs from path for ref selections. */
  actualPath: unknown[];
  min: number | null;
  max: number | null;
  remaining: number;
  optionCount: number;
  /** Keys of the options currently chosen. */
  selected: string[];
  ref?: unknown[];
  multiselect?: true;
  sequential?: true;
  requireValue?: true;
}

export interface Evaluation {
  built: BuiltCharacter;
  selections: Selection[];
}

/**
 * Builds a strict entity. The result is memoized on the entity's JSON text.
 * Throws when options.rules names an unsupported edition.
 */
export function evaluate(entity: StrictEntity, options?: EvaluateOptions): Evaluation;

export interface ImportedCharacter {
  /** The migrated strict entity, as parsed verbose Transit-JSON. Pass it to evaluate. */
  entity: object;
  /** The old app's top-level :db/id as a string, or null when there was none. */
  legacyId: string | null;
}

/**
 * Imports a character saved by the old app. Applies the from-strict
 * normalizations (R1 to R3, R6, R9), the legacy key migration (R7), and the
 * xps fix (R5), and removes the old :db/id values and the owner.
 */
export function importCharacter(entity: StrictEntity): ImportedCharacter;

/**
 * Normalizes an entity with char5e/from-strict and serializes it with
 * char5e/to-strict, as parsed verbose Transit-JSON. Selections stay arrays,
 * so their order is kept.
 */
export function exportCharacter(entity: StrictEntity): object;

/**
 * Options for the mutations that read the template. Defaults as for
 * evaluate.
 */
export type MutationOptions = EvaluateOptions;

/**
 * A key path as evaluate's selections report it, such as
 * ["class", "fighter", "skill-proficiency"].
 */
export type Path = (string | number)[];

/**
 * A value in the entity's Transit-JSON form: "~:key" is a keyword, and
 * anything else is plain JSON.
 */
export type TransitValue = unknown;

// The mutations take a strict entity and return a new one. They do what
// the old builder does for the same click or input, and throw with the
// reason where the builder would refuse it.

/** The builder's new character: a level 1 barbarian with its starting equipment. */
export function emptyCharacter(): object;

/**
 * Selects optionKey in the selection whose actualPath is path. Selecting a
 * background also adds its starting equipment. Throws for an option that
 * is already selected, fails its prerequisites, or has no selections left.
 */
export function select(entity: StrictEntity, path: Path, optionKey: string, options?: MutationOptions): object;

/**
 * Removes optionKey from the selection whose actualPath is path. Throws where
 * the builder cannot remove it, for example from a single-select.
 */
export function deselect(entity: StrictEntity, path: Path, optionKey: string, options?: MutationOptions): object;

/**
 * Sets a character value. A key without a namespace, such as
 * "character-name", is in orcpub.dnd.e5.character.
 */
export function setValue(entity: StrictEntity, key: string, value: TransitValue): object;

/**
 * Sets the value of the option at path, a selection's actualPath followed by
 * the option key: hit points, ability scores, or an item's quantity.
 */
export function setField(entity: StrictEntity, path: Path, value: TransitValue, options?: MutationOptions): object;

/** Adds a level to class classKey. */
export function addLevel(entity: StrictEntity, classKey: string, options?: MutationOptions): object;

/** Removes the highest level of class classKey and its selections. */
export function removeLevel(entity: StrictEntity, classKey: string, options?: MutationOptions): object;

/**
 * Replaces the class at index with classKey at level 1. For the first class,
 * this also replaces the class starting equipment.
 */
export function setClass(entity: StrictEntity, index: number, classKey: string, options?: MutationOptions): object;

/** Adds class classKey at level 1. It must meet its prerequisites. */
export function addClass(entity: StrictEntity, classKey: string, options?: MutationOptions): object;

/** Removes class classKey. Removing the first class makes the next class first. */
export function removeClass(entity: StrictEntity, classKey: string, options?: MutationOptions): object;

/** Replaces the background starting equipment with that of backgroundKey. */
export function addStartingEquipment(entity: StrictEntity, backgroundKey: string, options?: MutationOptions): object;

/** Adds one of itemKey, equipped, to an inventory selection such as "weapons". */
export function addInventoryItem(entity: StrictEntity, selectionKey: string, itemKey: string): object;

/** Removes itemKey from an inventory selection. */
export function removeInventoryItem(entity: StrictEntity, selectionKey: string, itemKey: string): object;

/**
 * Adds one to an ability in the ability score improvement at path, such as
 * ["class", "fighter", "levels", "level-4", "asi-or-feat",
 * "ability-score-improvement", "asi"]. An ability key without a namespace,
 * such as "str", is in orcpub.dnd.e5.character.
 */
export function increaseAbility(entity: StrictEntity, path: Path, abilityKey: string, options?: MutationOptions): object;

/** Removes one pick of an ability from the ability score improvement at path. */
export function decreaseAbility(entity: StrictEntity, path: Path, abilityKey: string, options?: MutationOptions): object;
