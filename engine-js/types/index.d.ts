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
