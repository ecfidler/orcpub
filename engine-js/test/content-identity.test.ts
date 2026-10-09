// Contract C3, content identity (orc-alchemy
// docs/plan/01-compatibility-contract.md): saved characters and .orcbrew
// files refer to content by key, so key derivation (common/name-to-kw, the
// explicit spell keys, subclass selection keys) must never change. This
// suite fails when it does: every fixture's option keys must still resolve.
import { describe, expect, it } from "vitest";
import { buildTemplate, importCharacter, keys, parseOrcbrew, reconcileMissingContent } from "@pubdoor/dmv";
import type { Homebrew, MissingContent, ReconcileReport, UnresolvedOption } from "@pubdoor/dmv";
import {
  baselineKeySets,
  builtInKeys,
  danglingReferences,
  fixtureMagicItems,
  fixtureNames,
  readFixture,
  readFixtureText,
} from "./support.js";

/** A missing-content item as .meta.json records it: without suggestions. */
type ReconcileItem = Pick<MissingContent, "contentType" | "key" | "path">;

interface Meta {
  orcbrew: string[];
  magicItems?: string;
  quirk?: string;
  /** The keys the fixture is known not to resolve (fixtures/README.md, finding 17). */
  unresolved?: { reason: string; items: ReconcileItem[]; unresolvedOptions: UnresolvedOption[] };
}

/** The character's packs, imported in order as the old app would. */
function homebrew(files: string[]): Homebrew | undefined {
  if (files.length === 0) return undefined;
  return files.reduce<Homebrew>((loaded, file) => {
    const text = readFixtureText(`orcbrew/${file}`);
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
    for (const name of fixtureNames(dir, ".strict.json")) {
      const meta = readFixture(`${dir}/${name}.meta.json`) as Meta;
      const label = `${dir}/${name}${meta.orcbrew.length ? ` with ${meta.orcbrew.join(", ")}` : ""}`;

      it(meta.unresolved ? `${label} leaves only its recorded keys unresolved` : `${label} resolves every key`, () => {
        const strict = readFixture(`${dir}/${name}.strict.json`) as object;
        // R5 and R7 fixtures are read as the new app reads them.
        const entity = meta.quirk === "R5" || meta.quirk === "R7" ? importCharacter(strict).entity : strict;
        const expected = {
          items: byPath(meta.unresolved?.items ?? []),
          unresolvedOptions: byPath(meta.unresolved?.unresolvedOptions ?? []),
        };
        const report = reconcileMissingContent(entity, homebrew(meta.orcbrew), { magicItems: fixtureMagicItems(meta) });

        expect(reported(report)).toStrictEqual(expected);
        expect(report.hasMissing).toBe(meta.unresolved !== undefined);
      });
    }
  }
});

describe("C3: every reference in a fixture pack resolves", () => {
  const builtIns = builtInKeys();

  it("reads the built-in classes and races from the template", () => {
    expect(builtIns.classes).toContain("wizard");
    expect(builtIns.races).toContain("elf");
  });

  // The old template looks a level-selections type up only in the loaded
  // packs' selections, so a template selection key builds a selection with
  // no name and no options. The check reports it.
  it("counts no template selection as built in", () => {
    const parsed = parseOrcbrew(
      `{:orcpub.dnd.e5/classes {:tinker {:key :tinker :name "Tinker" :option-pack "T" :hit-die 8
         :ability-increase-levels [4] :subclass-level 3 :subclass-title "Guild"
         :level-selections [{:type :skill-proficiency :level 1 :num 1}]}}}`,
      { name: "sk" },
    );
    expect(parsed.success).toBe(true);
    const tinker = buildTemplate(parsed.data!)
      .shape.find((selection) => selection.key === "class")!
      .options.find((option) => option.key === "tinker")!;
    const level1 = tinker.selections!
      .find((selection) => selection.key === "levels")!
      .options.find((option) => option.key === "level-1")!;
    const levelSelection = level1.selections!.find((selection) => selection.key === "skill-proficiency")!;

    expect(levelSelection.options).toStrictEqual([]);
    expect(danglingReferences(parsed.data!, builtIns)).toStrictEqual([
      { to: "selections", key: "skill-proficiency", from: "classes tinker" },
    ]);
  });

  for (const pack of fixtureNames("orcbrew", ".orcbrew")) {
    it(`${pack} refers only to built-in or same-file keys`, () => {
      const parsed = parseOrcbrew(readFixtureText(`orcbrew/${pack}.orcbrew`), { name: pack });
      expect(parsed.success).toBe(true);

      expect(danglingReferences(parsed.data!, builtIns)).toStrictEqual([]);
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
