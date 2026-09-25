import { readdirSync, readFileSync } from "node:fs";
import { describe, expect, it } from "vitest";
import { evaluate, importCharacter } from "@dmv/pubdoor";

// The M0 fixtures: fixtures/README.md describes the layout and how
// expected.json and selections.json were generated.
const fixtures = new URL("../../fixtures/", import.meta.url);

const SELECTIONS = "~:orcpub.entity.strict/selections";
const OPTIONS = "~:orcpub.entity.strict/options";
const SELECTION_KEY = "~:orcpub.entity.strict/key";

type Strict = Record<string, unknown> & { [SELECTIONS]: StrictSelection[] };
type StrictSelection = Record<string, unknown>;

function read(dir: string, file: string): unknown {
  return JSON.parse(readFileSync(new URL(`${dir}/${file}`, fixtures), "utf8"));
}

function fixtureNames(dir: string): string[] {
  return readdirSync(new URL(`${dir}/`, fixtures))
    .filter((file) => file.endsWith(".meta.json"))
    .map((file) => file.slice(0, -".meta.json".length))
    .sort();
}

type Meta = { orcbrew: string[]; quirk?: string };

function meta(dir: string, name: string): Meta {
  return read(dir, `${name}.meta.json`) as Meta;
}

// R5 and R7 fixtures expect the migrated entity, so they go through
// importCharacter first.
function needsImport(dir: string, name: string): boolean {
  const { quirk } = meta(dir, name);
  return quirk === "R5" || quirk === "R7";
}

function usesHomebrew(dir: string, name: string): boolean {
  return meta(dir, name).orcbrew.length > 0;
}

for (const dir of ["characters", "legacy"]) {
  describe(`golden ${dir}`, () => {
    for (const name of fixtureNames(dir)) {
      // Characters built with .orcbrew content need buildTemplate (M3).
      const test = usesHomebrew(dir, name) ? it.skip : it;

      test(name, () => {
        const strict = read(dir, `${name}.strict.json`) as object;
        const { built, selections } = evaluate(
          needsImport(dir, name) ? importCharacter(strict).entity : strict,
        );

        expect(built).toStrictEqual(read(dir, `${name}.expected.json`));
        expect(selections).toStrictEqual(read(dir, `${name}.selections.json`));
      });
    }
  });
}

// Selection order carries meaning (doc 02, wrinkle 3), so the suite must be
// able to see order lost at the JS boundary.
describe("selection order", () => {
  it("changes built when the top-level selections are reordered", () => {
    const strict = read("characters", "fighter-1.strict.json") as Strict;
    const reordered = { ...strict, [SELECTIONS]: [...strict[SELECTIONS]].reverse() };

    const before = evaluate(strict).built as Record<string, unknown>;
    const after = evaluate(reordered).built as Record<string, unknown>;

    expect(after).not.toEqual(before);
    // For fighter-1 the difference is the order of the traits list.
    expect(after["traits"]).not.toEqual(before["traits"]);
  });

  it("changes the multiclass result when the class order is reversed", () => {
    const strict = read("characters", "fighter-3-wizard-2.strict.json") as Strict;
    const reordered = {
      ...strict,
      [SELECTIONS]: strict[SELECTIONS].map((selection) =>
        selection[SELECTION_KEY] === "~:class"
          ? { ...selection, [OPTIONS]: [...(selection[OPTIONS] as unknown[])].reverse() }
          : selection,
      ),
    };

    const before = evaluate(strict).built as Record<string, unknown>;
    const after = evaluate(reordered).built as Record<string, unknown>;

    // The first class sets the saving throw proficiencies.
    expect(after["saving-throws"]).not.toEqual(before["saving-throws"]);
  });
});
