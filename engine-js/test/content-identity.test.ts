// Contract C3, content identity (orc-alchemy
// docs/plan/01-compatibility-contract.md): saved characters and .orcbrew
// files refer to content by key, so key derivation (common/name-to-kw, the
// explicit spell keys, subclass selection keys) must never change. This
// suite fails when it does: every fixture's option keys must still resolve.
import { readdirSync, readFileSync } from "node:fs";
import { describe, expect, it } from "vitest";
import { buildTemplate, importCharacter, keys, parseOrcbrew, reconcileMissingContent } from "@pubdoor/dmv";
import type { Homebrew, MissingContent, ReconcileReport, UnresolvedOption } from "@pubdoor/dmv";
import { baselineKeySets } from "./template-keys.js";

const fixtures = new URL("../../fixtures/", import.meta.url);

function read(path: string): unknown {
  return JSON.parse(readFileSync(new URL(path, fixtures), "utf8"));
}

function names(dir: string, suffix: string): string[] {
  return readdirSync(new URL(`${dir}/`, fixtures))
    .filter((file) => file.endsWith(suffix))
    .map((file) => file.slice(0, -suffix.length))
    .sort();
}

type Item = Pick<MissingContent, "contentType" | "key" | "path">;

interface Meta {
  orcbrew: string[];
  quirk?: string;
  /** The keys the fixture is known not to resolve (fixtures/README.md, finding 17). */
  unresolved?: { reason: string; items: Item[]; unresolvedOptions: UnresolvedOption[] };
}

/** The character's packs, imported in order as the old app would. */
function homebrew(files: string[]): Homebrew | undefined {
  if (files.length === 0) return undefined;
  return files.reduce<Homebrew>((loaded, file) => {
    const text = readFileSync(new URL(`orcbrew/${file}`, fixtures), "utf8");
    const parsed = parseOrcbrew(text, { name: file.slice(0, -".orcbrew".length), existing: loaded });
    expect(parsed.success).toBe(true);
    return parsed.data!;
  }, {});
}

const byPath = <T extends { path: string[] }>(list: T[]): T[] =>
  [...list].sort((a, b) => JSON.stringify(a.path).localeCompare(JSON.stringify(b.path)));

/** The report without suggestions, in path order. */
function reported(report: ReconcileReport) {
  return {
    items: byPath(report.items.map(({ contentType, key, path }) => ({ contentType, key, path }))),
    unresolvedOptions: byPath(report.unresolvedOptions),
  };
}

describe("C3: every fixture character's keys resolve", () => {
  for (const dir of ["characters", "legacy"]) {
    for (const name of names(dir, ".strict.json")) {
      const meta = read(`${dir}/${name}.meta.json`) as Meta;
      const label = `${dir}/${name}${meta.orcbrew.length ? ` with ${meta.orcbrew.join(", ")}` : ""}`;

      it(meta.unresolved ? `${label} leaves only its recorded keys unresolved` : `${label} resolves every key`, () => {
        const strict = read(`${dir}/${name}.strict.json`) as object;
        // R5 and R7 fixtures are read as the new app reads them.
        const entity = meta.quirk === "R5" || meta.quirk === "R7" ? importCharacter(strict).entity : strict;
        const expected = {
          items: byPath(meta.unresolved?.items ?? []),
          unresolvedOptions: byPath(meta.unresolved?.unresolvedOptions ?? []),
        };
        const report = reconcileMissingContent(entity, homebrew(meta.orcbrew));

        expect(reported(report)).toStrictEqual(expected);
        expect(report.hasMissing).toBe(meta.unresolved !== undefined);
      });
    }
  }
});

// Transit-JSON verbose: a keyword is "~:name", and a map's keyword keys are too.
const kw = (k: string): string => `~:${k}`;
const unkw = (s: string): string => s.replace(/^~:/, "");
type Plugin = Record<string, Record<string, Record<string, unknown>> | undefined>;
interface Reference {
  /** The content type the reference points into. */
  to: "classes" | "races" | "selections";
  key: string;
  /** The item that holds the reference. */
  from: string;
}

/**
 * Each content reference in a parsed pack, by the content type it refers to,
 * as import_validation.cljs key-reference-map lists them: a subclass's class,
 * a subrace's race, the classes on a spell's spell list, a class's or
 * subclass's spell-list-kw, a feat's race prerequisites, and the plugin
 * selections that level-selections use.
 */
function references(plugin: Plugin): Reference[] {
  const out: (Omit<Reference, "key"> & { key: unknown })[] = [];
  const items = (type: string) => Object.entries(plugin[kw(`orcpub.dnd.e5/${type}`)] ?? {});
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

describe("C3: every reference in a fixture pack resolves", () => {
  // The built-in classes and races are the SRD template's options.
  const summary = buildTemplate().summary;
  const builtIn = (key: string) => summary.find((selection) => selection.key === key)!.optionKeys;
  const builtIns = { classes: builtIn("class"), races: builtIn("race"), selections: [] as string[] };

  it("reads the built-in classes and races from the template", () => {
    expect(builtIns.classes).toContain("wizard");
    expect(builtIns.races).toContain("elf");
  });

  for (const pack of names("orcbrew", ".orcbrew")) {
    it(`${pack} refers only to built-in or same-file keys`, () => {
      const parsed = parseOrcbrew(readFileSync(new URL(`orcbrew/${pack}.orcbrew`, fixtures), "utf8"), { name: pack });
      expect(parsed.success).toBe(true);
      const plugins = Object.values(parsed.data!) as Plugin[];
      // Every pack in the file, so a multi-plugin file's packs see each other.
      const sameFile = (type: string) =>
        plugins.flatMap((plugin) => Object.keys(plugin[kw(`orcpub.dnd.e5/${type}`)] ?? {}).map(unkw));
      const known = {
        classes: new Set([...builtIns.classes, ...sameFile("classes")]),
        races: new Set([...builtIns.races, ...sameFile("races")]),
        selections: new Set([...builtIns.selections, ...sameFile("selections")]),
      };

      const dangling = plugins.flatMap(references).filter((ref) => !known[ref.to].has(ref.key));

      expect(dangling).toStrictEqual([]);
    });
  }
});

describe("C3: the SRD template's keys equal the baseline's", () => {
  const baseline = baselineKeySets();

  it("lists the baseline's selection keys", () => {
    expect(keys.selectionKeys({})).toStrictEqual(baseline.selectionKeys);
    expect(baseline.selectionKeys).toHaveLength(108);
  });

  // The baseline comes from the JVM, whose level-18 Magical Secrets misses
  // the 9th-level spells (fixtures/README.md finding 15). Three of them are
  // offered nowhere else in the SRD template, because the classes that have
  // them prepare their spells from the full list.
  it("lists the baseline's option keys and the 9th-level Magical Secrets spells", () => {
    const browserOnly = ["mass-heal", "storm-of-vengeance", "true-resurrection"];

    expect(baseline.optionKeys).toHaveLength(1641);
    expect(baseline.optionKeys.filter((k) => browserOnly.includes(k))).toStrictEqual([]);
    expect(keys.optionKeys({})).toStrictEqual([...baseline.optionKeys, ...browserOnly].sort());
  });
});
