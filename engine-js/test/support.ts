// Helpers shared by the test files: fixture reads, Transit-JSON keys, the
// C3 reference check, and template key sets.
import { readdirSync, readFileSync } from "node:fs";
import { gunzipSync } from "node:zlib";
import { buildTemplate, readServerEdn, type MagicItems, type TemplateSelection } from "@pubdoor/dmv";

/** The repository's fixtures/ directory. */
export const fixtures = new URL("../../fixtures/", import.meta.url);

/** A fixture file's text. path is relative to fixtures/. */
export function readFixtureText(path: string): string {
  return readFileSync(new URL(path, fixtures), "utf8");
}

/** A fixture JSON file, parsed. path is relative to fixtures/. */
export function readFixture(path: string): unknown {
  return JSON.parse(readFixtureText(path));
}

/**
 * A fixture's custom magic items, as its .meta.json names them under
 * magicItems, read as the app reads the server's response. undefined when
 * it names none.
 */
export function fixtureMagicItems(meta: { magicItems?: string }): MagicItems | undefined {
  return meta.magicItems ? readServerEdn(readFixtureText(`magic-items/${meta.magicItems}`)) : undefined;
}

/** The names of the files in fixtures/<dir> that end in suffix, without it, sorted. */
export function fixtureNames(dir: string, suffix: string): string[] {
  return readdirSync(new URL(`${dir}/`, fixtures))
    .filter((file) => file.endsWith(suffix))
    .map((file) => file.slice(0, -suffix.length))
    .sort();
}

// Transit-JSON verbose: a keyword is "~:name", and a map's keyword keys are too.

/** The Transit-JSON form of a keyword: "spark" is "~:spark". */
/** A real export, git-ignored, for the tests that run only when it is present. */
export const privateExport = new URL("orcbrew/private/all-content3.orcbrew", fixtures);

export const kw = (k: string): string => `~:${k}`;

/** The keyword's name without the "~:" prefix. */
export const unkw = (s: string): string => s.replace(/^~:/, "");

/**
 * Each pack's content keys per type, as a fixture's "plugins" block lists
 * them: pack name → "orcpub.dnd.e5/<type>" → sorted keys.
 */
export function contentKeys(data: Record<string, object>): Record<string, Record<string, string[]>> {
  return Object.fromEntries(
    Object.entries(data).map(([pack, plugin]) => [
      pack,
      Object.fromEntries(
        Object.entries(plugin as Record<string, unknown>)
          .filter(([, items]) => items !== null && typeof items === "object" && !Array.isArray(items))
          .map(([type, items]) => [unkw(type), Object.keys(items as object).map(unkw).sort()]),
      ),
    ]),
  );
}

/** One homebrew item, such as a class, as Transit-JSON. */
export type ContentItem = Record<string, unknown>;

/** One content type's items in a pack, by Transit-JSON key. */
export type ContentMap = Record<string, ContentItem>;

/** One pack of homebrew as Transit-JSON: content maps by "~:orcpub.dnd.e5/<type>". */
export type Plugin = Record<string, ContentMap | undefined>;

/** The items of one content type, such as "classes", in a pack. */
export function contentOf(plugin: Plugin, type: string): ContentMap {
  return plugin[kw(`orcpub.dnd.e5/${type}`)] ?? {};
}

/** A key that one homebrew item uses to refer to another item. */
export interface Reference {
  /** The content type the reference points into. */
  to: "classes" | "races" | "selections";
  key: string;
  /** The item that holds the reference. */
  from: string;
}

/**
 * Each content reference in a pack, by the content type it refers to, as
 * import_validation.cljs key-reference-map lists them: a subclass's class,
 * a subrace's race, the classes on a spell's spell list, a class's or
 * subclass's spell-list-kw, a feat's race prerequisites, and the plugin
 * selections that level-selections use.
 */
export function references(plugin: Plugin): Reference[] {
  const out: (Omit<Reference, "key"> & { key: unknown })[] = [];
  const items = (type: string) => Object.entries(contentOf(plugin, type));
  for (const [k, item] of items("subclasses")) {
    out.push({ to: "classes", key: item[kw("class")], from: `subclass ${unkw(k)}` });
  }
  for (const [k, item] of items("subraces")) {
    out.push({ to: "races", key: item[kw("race")], from: `subrace ${unkw(k)}` });
  }
  for (const [k, item] of items("spells")) {
    for (const cls of Object.keys((item[kw("spell-lists")] ?? {}) as object)) {
      out.push({ to: "classes", key: cls, from: `spell ${unkw(k)}` });
    }
  }
  for (const [k, item] of items("feats")) {
    const races = (item[kw("path-prereqs")] as Record<string, object> | undefined)?.[kw("race")] ?? {};
    for (const race of Object.keys(races)) out.push({ to: "races", key: race, from: `feat ${unkw(k)}` });
  }
  for (const type of ["classes", "subclasses"]) {
    for (const [k, item] of items(type)) {
      const spellListKw = (item[kw("spellcasting")] as Record<string, unknown> | undefined)?.[kw("spell-list-kw")];
      if (spellListKw != null) out.push({ to: "classes", key: spellListKw, from: `${type} ${unkw(k)}` });
      for (const selection of (item[kw("level-selections")] ?? []) as Record<string, unknown>[]) {
        out.push({ to: "selections", key: selection[kw("type")], from: `${type} ${unkw(k)}` });
      }
    }
  }
  // A missing reference shows as "undefined", which never resolves.
  return out.map((ref) => ({ ...ref, key: typeof ref.key === "string" ? unkw(ref.key) : String(ref.key) }));
}

export type BuiltInKeys = Record<Reference["to"], string[]>;

/**
 * The keys a pack can refer to without defining them: the SRD template's
 * classes and races. There are no built-in selections. A level-selections
 * type is looked up only in the plugin selections (::selections5e/selection-map,
 * spell_subs.cljs:105-115, and selection-map in facade/template.cljs), so a
 * template selection key such as skill-proficiency builds an empty
 * selection there.
 */
export function builtInKeys(): BuiltInKeys {
  const summary = buildTemplate().summary;
  const optionKeys = (key: string) => summary.find((selection) => selection.key === key)!.optionKeys;
  return { classes: optionKeys("class"), races: optionKeys("race"), selections: [] };
}

/**
 * The references in homebrew that name neither a built-in key nor an item
 * of homebrew itself. Every pack counts, so a multi-plugin file's packs see
 * each other.
 */
export function danglingReferences(homebrew: Record<string, object>, builtIns: BuiltInKeys): Reference[] {
  const plugins = Object.values(homebrew) as Plugin[];
  const defined = (type: Reference["to"]) =>
    new Set([...builtIns[type], ...plugins.flatMap((plugin) => Object.keys(contentOf(plugin, type)).map(unkw))]);
  const known = { classes: defined("classes"), races: defined("races"), selections: defined("selections") };
  return plugins.flatMap(references).filter((ref) => !known[ref.to].has(ref.key));
}

export interface KeySets {
  selectionKeys: string[];
  optionKeys: string[];
}

/** The distinct selection and option keys of a template shape, sorted. */
export function keySets(shape: TemplateSelection[]): KeySets {
  const selectionKeys = new Set<string>();
  const optionKeys = new Set<string>();
  const walk = (selection: TemplateSelection): void => {
    selectionKeys.add(selection.key);
    for (const option of selection.options) {
      optionKeys.add(option.key);
      option.selections?.forEach(walk);
    }
  };
  shape.forEach(walk);
  return { selectionKeys: [...selectionKeys].sort(), optionKeys: [...optionKeys].sort() };
}

/**
 * The C3 content-identity baseline: every built-in selection and option key
 * of the JVM oracle's SRD template (fixtures/orcbrew/_srd-baseline.template.json.gz).
 */
export function baselineKeySets(): KeySets {
  const gz = readFileSync(new URL("orcbrew/_srd-baseline.template.json.gz", fixtures));
  const { shape } = JSON.parse(gunzipSync(gz).toString("utf8")) as { shape: TemplateSelection[] };
  return keySets(shape);
}
