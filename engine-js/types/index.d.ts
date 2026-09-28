/**
 * A strict entity (orcpub.entity.strict/entity) as Transit-JSON: the text,
 * or the value JSON.parse returns for it. The fixtures under
 * fixtures/characters/*.strict.json use this format. Treat it as opaque:
 * store it and pass it back, and change it only with the functions below.
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

// The built character is plain JSON data keyed by the old app's
// character-subs names, converted as fixtures/README.md §expected.json
// describes: a keyword becomes "ns/name", a set becomes a sorted array, and
// a map keyed by vectors becomes { __entries: [[key, value], ...] }. A key
// whose accessor returns nothing is null.
//
// The types split it in two. BuiltCharacter is the edition-neutral sheet, the
// part an app's adapter should depend on. Built2014 adds the 2014-shaped
// keys and every other accessor. evaluate returns a Built2014, which is also
// a BuiltCharacter. The object itself is not split.

/** An ability key, such as "orcpub.dnd.e5.character/str". */
export type AbilityKey = `orcpub.dnd.e5.character/${"str" | "dex" | "con" | "int" | "wis" | "cha"}`;

/** A value for each ability. */
export type Abilities = Record<AbilityKey, number>;

/** A class's entry in levels, keyed by class key. */
export interface ClassLevels {
  "class-level": number;
  "class-name": string;
  "hit-die": number;
  /** The subclass key, once one is chosen. */
  subclass?: string;
  "subclass-name"?: string;
}

/**
 * An inventory entry, keyed by item key. quantity is a string only when
 * evaluate is given an old entity that did not go through importCharacter
 * (quirk R6).
 */
export interface InventoryItem {
  "orcpub.dnd.e5.character.equipment/quantity": number;
  "orcpub.dnd.e5.character.equipment/equipped?": boolean;
  "orcpub.dnd.e5.character.equipment/background-starting-equipment?"?: boolean;
  "orcpub.dnd.e5.character.equipment/class-starting-equipment?"?: boolean;
}

/** Items keyed by item key, or null when there are none. */
export type Inventory = Record<string, InventoryItem> | null;

/** The attack and damage bonuses of one carried weapon. */
export interface WeaponModifiers {
  attack: { standard: number; finesse: number };
  "best-attack": number;
  damage: { standard: number; finesse: number; "off-hand": number };
  "best-damage": number;
  "best-damage-off-hand": number;
  "dual-wield?": boolean;
  "has-prof?": boolean;
}

/** A special attack, such as the dragonborn Breath Weapon or the monk's Martial Arts. */
export interface Attack {
  name: string;
  summary?: string;
  page?: number;
  /** For example "melee" or "area". */
  "attack-type"?: string;
  "damage-type"?: string;
  "damage-die"?: number;
  "damage-die-count"?: number;
  "damage-modifier"?: number;
  save?: AbilityKey;
  "save-dc"?: number;
}

/** An amount of a unit, such as { amount: 1, units: "long-rest" }. */
export interface Amount {
  amount: number;
  units: string;
}

/** A trait, action, bonus action, or reaction. */
export interface Feature {
  name: string;
  summary?: string;
  description?: string;
  page?: number;
  source?: string | null;
  /** The class the feature comes from, for a class feature. */
  "class-key"?: string | null;
  /** The level the feature starts at. */
  level?: number;
  frequency?: Amount;
  duration?: Amount;
}

/** A known spell. */
export interface KnownSpell {
  key: string;
  /** The class or race name the spell is known through, such as "Wizard". */
  class: string;
  /** The spellcasting ability. */
  ability: AbilityKey;
  qualifier?: string | null;
}

/** A class's or race's spellcasting numbers, keyed by its name. */
export interface SpellModifiers {
  class: string;
  ability: AbilityKey;
  "spell-save-dc": number;
  "spell-attack-modifier": number;
}

/**
 * The edition-neutral part of the built character: what a character sheet
 * reads. A proficiency map's inner keys name each source, "nil" when it has
 * none.
 */
export interface BuiltCharacter {
  // Identity
  "character-name": string | null;
  race: string | null;
  subrace: string | null;
  background: string | null;
  /** Class keys, the first class first. */
  classes: string[];
  levels: Record<string, ClassLevels>;
  "total-levels": number;
  "class-level": Record<string, number>;

  // Abilities, saves, and skills
  abilities: Abilities;
  "ability-bonuses": Abilities;
  "proficiency-bonus": number;
  /** Proficient saves, as ability keys. */
  "saving-throws": AbilityKey[];
  "save-bonuses": Abilities;
  /** Skill key to its sources. */
  "skill-profs": Record<string, Record<string, boolean>> | null;
  "skill-bonuses": Record<string, number>;
  "skill-expertise": string[] | null;
  "passive-perception": number;
  initiative: number;

  // Other proficiencies
  "armor-profs": string[];
  "weapon-profs": string[];
  /** Tool key to its sources. */
  "tool-profs": Record<string, Record<string, boolean>>;
  "tool-bonus": Record<string, number>;
  languages: string[] | null;

  // Armor class and hit points
  /** Armor class without armor or shield. */
  "armor-class": number;
  /** Armor class for every carried armor and shield, each also null. */
  "armor-class-with-armor": { armor: string | null; shield: string | null; ac: number }[];
  "max-hit-points": number;
  "current-hit-points": number | null;

  // Speed, in feet
  "base-land-speed": number;
  "base-flying-speed": number;
  "base-swimming-speed": number;

  // Attacks
  /** Keyed by carried weapon key. null for a weapon key the content does not have. */
  "weapon-modifiers": Record<string, WeaponModifiers | null>;
  "number-of-attacks": number;
  attacks: Attack[] | null;

  // Spells
  /** Spell level to slot count, such as { "1": 4, "2": 3 }. */
  "spell-slots": Record<string, number>;
  /** Spell level to the spells known at that level, keyed by [class name, spell key]. */
  "spells-known": Record<string, { __entries: [[string, string], KnownSpell][] }>;
  "spell-modifiers": Record<string, SpellModifiers>;
  "spell-save-dc": Abilities;
  "spell-attack-modifier": Abilities;
  /** Class name to true for each class that prepares spells. */
  "prepares-spells": Record<string, boolean> | null;
  /** Class name to the number of spells it can prepare. */
  "prepare-spell-count": Record<string, number>;

  // Features
  traits: Feature[];
  actions: Feature[];
  "bonus-actions": Feature[];
  reactions: Feature[];
  feats: string[] | null;

  // Equipment
  weapons: Inventory;
  armor: Inventory;
  equipment: Inventory;
  treasure: Inventory;
  "magic-weapons": Inventory;
  "magic-armor": Inventory;
  "magic-items": Inventory;
}

/**
 * The whole built character for the 2014 rules: the sheet, the keys shaped
 * by the 2014 rules, and every other accessor in fixtures/README.md
 * §expected.json as unknown. When a sheet needs another edition-neutral
 * accessor, such as darkvision, name it in BuiltCharacter.
 */
export interface Built2014 extends BuiltCharacter {
  /**
   * Ability key to increase. Homebrew can use unqualified keys such as
   * "con" (drift form 10), so the keys are not always AbilityKeys.
   */
  "race-ability-increases": Record<string, number> | null;
  "subrace-ability-increases": Record<string, number> | null;
  /** Class key to its spellcaster level factor, for multiclass spell slots. */
  "spell-slot-factors": Record<string, number> | null;
  "total-spellcaster-levels": number;
  /** Class name to how it learns spells, such as "schedule". */
  "spells-known-modes": Record<string, string> | null;
  "pact-magic?": boolean | null;
  /** The content sources the character's options may come from. */
  "option-sources": string[] | null;
  /** Adventurers League: why the character is not legal, or null. */
  "al-illegal-reasons": string[] | null;
  [key: string]: unknown;
}

/** One entry of entity/available-selections, flattened. */
export interface AvailableSelection {
  key: string;
  name: string;
  path: string[];
  /** The path where the selection's data is stored. Differs from path for ref selections. */
  actualPath: string[];
  min: number | null;
  max: number | null;
  remaining: number;
  optionCount: number;
  /** Keys of the options currently chosen. */
  selected: string[];
  ref?: string[];
  multiselect?: true;
  sequential?: true;
  requireValue?: true;
}

export interface Evaluation {
  built: Built2014;
  selections: AvailableSelection[];
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

export interface AutofillOptions extends MutationOptions {
  /** A 32-bit integer. The same seed and entity give the same result. Without one, Math.random. */
  seed?: number;
  /** Selection paths to keep, as the builder's locked components, such as [["race"]]. */
  keep?: Path[];
  /** Keep every option and value, and fill only the selections with picks remaining. */
  keepAll?: boolean;
}

/**
 * Fills a character at random, as the builder's random character button
 * does. By default it keeps only the options at options.keep and the
 * enabled plugins (optional-content), fills the rest, including the class
 * and level, and drops the values such as the name. With keepAll, it fills only what is unfilled. Names are not
 * generated.
 *
 * Unlike the old button, it backtracks from a choice that opens a
 * selection nothing can fill, such as a feat when the SRD's one feat is
 * taken, and picks another. It never undoes an option the entity already
 * had, so with keepAll such a selection can stay unfilled.
 *
 * Count a ref selection's remaining across its occurrences, as
 * entity/combine-selections does: each occurrence in evaluate's selections
 * counts every pick at the shared path, so a filled ref selection reports
 * zero or less there.
 */
export function autofill(entity: StrictEntity, options?: AutofillOptions): object;
