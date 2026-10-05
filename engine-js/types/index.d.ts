/**
 * A strict entity (orcpub.entity.strict/entity) as Transit-JSON: the text,
 * or the value JSON.parse returns for it. The fixtures under
 * fixtures/characters/*.strict.json use this format. Treat it as opaque:
 * store it and pass it back, and change it only with the functions below.
 */
export type StrictEntity = string | object;

/** The rules edition. The engine supports only "2014". */
export type Rules = "2014";

export interface EvaluateOptions {
  /** Defaults to "2014". Any other value throws. */
  rules?: Rules;
  /**
   * The loaded packs: the data of the last parseOrcbrew call. Without it,
   * the character builds against the SRD only.
   */
  homebrew?: Homebrew;
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
 * An inventory entry, keyed by item key. The type describes entities that
 * went through importCharacter, which parses a string quantity (quirk R6).
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
  /** Added to the land speed without armor, such as the monk's Unarmored Movement. */
  "unarmored-speed-bonus": number | null;
  /**
   * Land speed without armor (armor null), then in each carried armor that
   * is not a shield. null unless a feature makes the speed depend on armor,
   * such as the barbarian's Fast Movement.
   */
  "speed-with-armor": { armor: string | null; speed: number }[] | null;
  /** Darkvision range, 0 without darkvision. */
  darkvision: number;

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
 * A key path the mutations accept, such as a selection's actualPath:
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

/**
 * Homebrew: the multi-plugin map, the old app's :plugins, as verbose
 * Transit-JSON. That is the text, or the value JSON.parse returns for it.
 * Each top-level key is a pack name, and each value is that pack's
 * single-plugin map, such as { "~:orcpub.dnd.e5/spells": { "~:fireball":
 * {...} } }. parseOrcbrew merges a file into it. Store each pack as it
 * is, and remove an item by deleting its entry.
 */
export type Homebrew = string | Record<string, object>;

export interface ParseOrcbrewOptions {
  /**
   * The pack name for a single-plugin file. The old app used the file name
   * without .orcbrew. Defaults to "Imported Content".
   */
  name?: string;
  /**
   * The homebrew already loaded. The file is merged into it, and its keys
   * are checked for external conflicts.
   */
  existing?: Homebrew;
  /** Import all or nothing instead of skipping invalid items. */
  strict?: boolean;
}

/** One item the progressive import left out. */
export interface SkippedItem {
  key: string;
  errors: unknown;
}

/** The old app's import log for one file. Keywords are "ns/name" strings. */
export interface ImportLog {
  /** Each automatic fix, {type, description, ...}. */
  changes: object[];
  errors: string[];
  "skipped-items": SkippedItem[];
  /** The raw duplicate-key report. */
  "key-conflicts"?: { "internal-conflicts": object[]; "external-conflicts": object[] };
  "key-warnings"?: object[];
  "imported-count"?: number;
  "skipped-count"?: number;
  /** The notice the old app showed after the import. */
  message: string;
  "parse-error"?: boolean;
  line?: number | null;
  hint?: string;
}

/** A key conflict with suggested replacement keys, as the old conflict modal lists it. */
export interface KeyConflict {
  id: string;
  type: "internal" | "external";
  key: string;
  "content-type": string;
  "content-type-name": string;
  /** internal: the packs that share the key. */
  sources?: object[];
  /** internal: a suggested key for each source. */
  "suggested-renames"?: { source: string; "new-key": string }[];
  /** external: the imported item. */
  "import-source"?: string;
  "import-name"?: string;
  /** external: the loaded item it collides with. */
  "existing-source"?: string;
  "existing-name"?: string;
  /** external: a suggested key for the imported item. */
  "suggested-new-key"?: string;
}

export interface ParsedOrcbrew {
  success: boolean;
  /**
   * options.existing with the file merged in, as the old app merged it. A
   * single-plugin file replaces the pack under options.name. A multi-plugin
   * file's packs merge into same-named packs one content type at a time.
   * Load it in place of the existing homebrew. null when success is false.
   */
  data: Record<string, object> | null;
  log: ImportLog;
  conflicts: KeyConflict[];
  skipped: SkippedItem[];
}

/**
 * Reads .orcbrew text through the old importer: text fixes, parsing,
 * Unicode normalization, data cleaning, placeholders for required fields,
 * option deduplication, duplicate-key detection, and validation. It also
 * fixes two forms the old engine ignored and logs each fix in log.changes:
 * a bare ability key such as :con becomes :orcpub.dnd.e5.character/con
 * wherever the engine reads ability keys (type "normalized-ability-key"),
 * and a skill-options or multiclass-skill-options without choose gets
 * choose 1 (type "defaulted-choose"). A leading
 * byte-order mark is removed. The result's data is all the loaded homebrew,
 * not only the file's packs. Key conflicts do not stop the import. The old
 * app asked the user to resolve them before it loaded the data.
 */
export function parseOrcbrew(text: string, options?: ParseOrcbrewOptions): ParsedOrcbrew;

/** A modifier in a template shape. name and value appear when they are plain data. */
export interface TemplateModifier {
  key: string | null;
  name?: string;
  value?: string | number | boolean;
}

/** An option in a template shape. */
export interface TemplateOption {
  key: string;
  name: string;
  order?: number;
  modifiers?: TemplateModifier[];
  /** How many prereqs the option has. */
  prereqs?: number;
  selections?: TemplateSelection[];
}

/** A selection in a template shape. */
export interface TemplateSelection {
  key: string;
  name: string;
  min: number | null;
  max: number | null;
  options: TemplateOption[];
  ref?: string[];
  order?: number;
  tags?: string[];
  multiselect?: true;
  sequential?: true;
  requireValue?: true;
}

/** A top-level selection's picks and its option keys in template order. */
export interface TemplateSummaryEntry {
  key: string;
  min: number | null;
  max: number | null;
  optionKeys: string[];
}

export interface BuiltTemplate {
  summary: TemplateSummaryEntry[];
  /** The template's top-level selections. */
  shape: TemplateSelection[];
  /**
   * The old subscription chain's lists: races, backgrounds, classes, feats,
   * languages, invocations, boons, plugin-spells, plugin-subraces,
   * plugin-subclasses, plugin-selections, and plugin-monsters. Each item is
   * its key, or a background its name.
   */
  content: Record<string, string[]>;
}

/**
 * Builds the template for the homebrew, or for the SRD alone without it, and
 * describes it as plain data. The template is the one evaluate uses for the
 * same homebrew.
 */
export function buildTemplate(homebrew?: Homebrew): BuiltTemplate;

/**
 * The key namespace of a template: the keys that saved characters and
 * .orcbrew files refer to (contract C3). Each function builds the template
 * for the homebrew, or for the SRD alone without it, as buildTemplate does,
 * and returns each key once, sorted.
 */
export const keys: {
  /** Every selection key in the template, at any depth. */
  selectionKeys(homebrew?: Homebrew): string[];
  /** Every option key in the template, at any depth. */
  optionKeys(homebrew?: Homebrew): string[];
};

/**
 * An item of a content list in dist/content. A keyword is an "ns/name"
 * string, as in BuiltCharacter. Functions, :modifiers, and :selections are
 * left out.
 */
export interface ContentItem {
  key: string;
  name: string;
  [field: string]: unknown;
}

/** An item of content/spells.json. */
export interface ContentSpell extends ContentItem {
  level: number;
  school: string;
  "casting-time": string;
  range: string;
  duration: string;
  components: Record<string, unknown>;
  description: string;
}

/** An item of content/monsters.json. */
export interface ContentMonster extends ContentItem {
  size: string;
  type: string;
  alignment: string;
  /** The challenge rating, such as 0.125 for 1/8. */
  challenge: number;
  "armor-class": number;
  "hit-points": Record<string, unknown>;
  str: number;
  dex: number;
  con: number;
  int: number;
  wis: number;
  cha: number;
}

/**
 * The content lists, keyed by file name. Import one with
 * `import("@pubdoor/dmv/content/<name>.json", { with: { type: "json" } })`
 * and cast it to its entry here: the export has no types condition. Each
 * holds the SRD content only, in the engine's order.
 */
export interface ContentLists {
  /** The classes: key and name. Their choices are in buildTemplate().shape. */
  classes: ContentItem[];
  /** The races, with their subraces and traits. */
  races: ContentItem[];
  backgrounds: ContentItem[];
  /** The feats: key and name. */
  feats: ContentItem[];
  languages: ContentItem[];
  spells: ContentSpell[];
  monsters: ContentMonster[];
  /** The magic items, with each weapon and armor item expanded into one item per base item. */
  "magic-items": ContentItem[];
  weapons: ContentItem[];
  ammunition: ContentItem[];
  armor: ContentItem[];
  /** The adventuring gear, tools, packs, mounts, and vehicles. */
  equipment: ContentItem[];
  /** Coins and gems. */
  treasure: ContentItem[];
}

export interface ExportOptions {
  /** The one pack to export or check. Without it, all packs. */
  pack?: string;
}

/** One item's missing required fields. */
export interface MissingFieldsItem {
  key: string;
  name: string | null;
  /** The missing fields, such as "name". */
  "missing-fields": string[];
  /** How many of the item's traits have no name. */
  "traits-missing-names": number;
}

/**
 * An item that the old `::e5/plugins` spec or a re-import would reject or
 * change.
 */
export interface ItemProblem {
  /** The content type, such as "orcpub.dnd.e5/feats". */
  "content-type": string;
  /** The item's map key. */
  key: string;
  /**
   * "key" when the item's key is not its map key, "option-pack" when its
   * option-pack is blank, and "nil" when it has a nil that the importer
   * would remove or replace.
   */
  rule: "key" | "option-pack" | "nil";
}

/** A pack's validateForExport result. */
export interface PackExportCheck {
  /** false when the old check fails or the pack has itemProblems. */
  valid: boolean;
  warnings: string[];
  errors: string[];
  /** The items without a required field, per content type. */
  missingFields: { "content-type": string; "invalid-items": MissingFieldsItem[] }[];
  /**
   * true when an item is missing a required field. The old check then
   * skips the full spec check, so errors lists no spec problems.
   */
  hasMissingRequiredFields: boolean;
  /** The items with a bad key, a blank option-pack, or a nil. */
  itemProblems: ItemProblem[];
}

export interface ExportCheck {
  /** false when any checked pack is invalid. */
  valid: boolean;
  /** Each checked pack's result, by pack name. */
  packs: Record<string, PackExportCheck>;
  /**
   * The homebrew with the checked packs fixed: placeholders for missing
   * required fields, as the old app's "export anyway" filled them, and each
   * item in itemProblems repaired. Pass it to orcbrewToEdn to export anyway.
   */
  filled: Record<string, object>;
}

/**
 * Checks packs before export, as the old app's export buttons did, and
 * checks each item's key, option-pack, and nils. The old app exported a
 * single pack whose only problem was missing fields once the user chose
 * "export anyway" (filled), and refused to export any other invalid pack.
 * Its export of all packs had no "export anyway", so filled is a
 * convenience there. Throws if options.pack is not in homebrew.
 */
export function validateForExport(homebrew: Homebrew, options?: ExportOptions): ExportCheck;

export interface OrcbrewToEdnOptions extends ExportOptions {
  /** Pretty-print the text, as the old pretty-print export did. */
  pretty?: boolean;
}

/**
 * The .orcbrew text of the homebrew, as the old app wrote it. With
 * options.pack, that pack alone as a single-plugin map, which the old app
 * imports under the file's name. Without it, all packs as the multi-plugin
 * map, the old app's all-content.orcbrew. Nothing is validated or changed:
 * run validateForExport first. The packs are written as given, and the old
 * app has no magic-item content type, so leave magic items out of the
 * homebrew to omit them. Throws if options.pack is not in homebrew.
 */
export function orcbrewToEdn(homebrew: Homebrew, options?: OrcbrewToEdnOptions): string;

/**
 * A homebrew content type, as the old importer names it
 * (import_validation.cljs content-type-names).
 */
export type ContentType =
  | "orcpub.dnd.e5/classes"
  | "orcpub.dnd.e5/subclasses"
  | "orcpub.dnd.e5/races"
  | "orcpub.dnd.e5/subraces"
  | "orcpub.dnd.e5/backgrounds"
  | "orcpub.dnd.e5/feats"
  | "orcpub.dnd.e5/spells"
  | "orcpub.dnd.e5/monsters"
  | "orcpub.dnd.e5/invocations"
  | "orcpub.dnd.e5/boons"
  | "orcpub.dnd.e5/selections"
  | "orcpub.dnd.e5/languages"
  | "orcpub.dnd.e5/encounters";

/** One key rename in one pack, as the old conflict modal's "rename" applies it. */
export interface KeyRename {
  /** The pack that holds the item, such as a KeyConflict's import-source. */
  pack: string;
  /** The content type, such as a KeyConflict's content-type. */
  contentType: ContentType;
  /** The item's current key, such as "artificer". */
  from: string;
  /** The new key, such as a KeyConflict's suggested-new-key. */
  to: string;
}

/**
 * Renames one item's key in one pack and rewrites that pack's references
 * to it (patch D4): a renamed class's subclasses, the spells that list it,
 * and the classes and subclasses that use its spell list; a renamed race's
 * subraces and the feats that require it; a renamed selection's
 * level-selections. The item's own key field is set to the new key. Other
 * packs and saved characters are not changed. Returns the new homebrew.
 * Throws if the pack is not loaded, if it has no item under from, or if it
 * already has one under to.
 */
export function renameKey(homebrew: Homebrew, rename: KeyRename): Record<string, object>;

/** A content type that reconcileMissingContent reports with suggestions. */
export type MissingContentType = "race" | "subrace" | "background" | "class" | "subclass" | "feat";

/**
 * Loaded content that might replace a missing key, scored as the old
 * content_reconciliation.cljs scores it.
 */
export interface ContentSuggestion {
  key: string;
  /** The display name. A homebrew class's name ends with its pack, as in the old class list. */
  name: string | null;
  /** The pack the content comes from, or null when the item names none. */
  source: string | null;
  /**
   * 1 for the same key, 0.8 for the same part before the first dash, 0.7
   * when one key starts with the other, and at least 0.6 when the item's
   * name gives the same base. Only scores above 0.3 are suggested.
   */
  similarity: number;
}

/** A race, subrace, background, class, subclass, or feat that does not resolve. */
export interface MissingContent {
  key: string;
  contentType: MissingContentType;
  /** The old report's label, such as "Subclass". */
  label: string;
  /**
   * The option's path of keys, without indices, such as ["class",
   * "fighter", "levels", "level-3", "martial-archetype", "eldritch-knight"].
   */
  path: string[];
  /**
   * The old report's guess at the source from the key's suffix: the words
   * after the first dash, capitalized. null for a key without a dash.
   */
  inferredSource: string | null;
  /** Up to 5 loaded items from the homebrew's packs, best first. */
  suggestions: ContentSuggestion[];
}

/** Any other option that does not resolve, under a parent that does. */
export interface UnresolvedOption {
  key: string;
  /** As MissingContent.path. */
  path: string[];
}

export interface ReconcileReport {
  /** true when items or unresolvedOptions is not empty. */
  hasMissing: boolean;
  /** Each unresolved content reference, even under an unresolved parent. */
  items: MissingContent[];
  /**
   * Each other unresolved option whose parent option resolves. An
   * unresolved option under an unresolved parent is not listed, because
   * its ancestor is.
   */
  unresolvedOptions: UnresolvedOption[];
}

/**
 * Checks every option key in the entity against the template for the
 * homebrew, or for the SRD alone without it, and reports the keys that do
 * not resolve (quirk R8). An option resolves when evaluate would find it
 * in the template. Nothing is changed: the entity keeps every choice, so
 * the app can offer a remap or ask for the pack to be imported first.
 */
export function reconcileMissingContent(entity: StrictEntity, homebrew?: Homebrew): ReconcileReport;
