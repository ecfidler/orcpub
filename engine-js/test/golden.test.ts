import { readdirSync, readFileSync } from "node:fs";
import { describe, expect, it } from "vitest";
import { evaluate } from "@dmv/pubdoor";

// The M0 fixtures: fixtures/README.md describes the layout and how
// expected.json and selections.json were generated.
const fixtures = new URL("../../fixtures/", import.meta.url);

const SELECTIONS = "~:orcpub.entity.strict/selections";
const OPTIONS = "~:orcpub.entity.strict/options";
const KEY = "~:orcpub.entity.strict/key";

type Strict = Record<string, unknown> & { [SELECTIONS]: StrictSelection[] };
type StrictSelection = Record<string, unknown>;

function read(dir: string, file: string): unknown {
  return JSON.parse(readFileSync(new URL(`${dir}/${file}`, fixtures), "utf8"));
}

function names(dir: string): string[] {
  return readdirSync(new URL(`${dir}/`, fixtures))
    .filter((file) => file.endsWith(".meta.json"))
    .map((file) => file.slice(0, -".meta.json".length))
    .sort();
}

function usesHomebrew(dir: string, name: string): boolean {
  const meta = read(dir, `${name}.meta.json`) as { orcbrew: string[] };
  return meta.orcbrew.length > 0;
}

for (const dir of ["characters", "legacy"]) {
  describe(`golden ${dir}`, () => {
    for (const name of names(dir)) {
      // Characters built with .orcbrew content need buildTemplate (M3).
      const test = usesHomebrew(dir, name) ? it.skip : it;

      test(name, () => {
        const { built, selections } = evaluate(
          read(dir, `${name}.strict.json`) as object,
        );

        expect(built).toEqual(read(dir, `${name}.expected.json`));
        expect(selections).toEqual(read(dir, `${name}.selections.json`));
      });
    }
  });
}

// Risk register: selection order carries meaning, so the suite must be able
// to see order lost at the JS boundary.
describe("selection order", () => {
  it("changes built when the top-level selections are reordered", () => {
    const strict = read("characters", "fighter-1.strict.json") as Strict;
    const reordered = { ...strict, [SELECTIONS]: [...strict[SELECTIONS]].reverse() };

    expect(evaluate(reordered).built).not.toEqual(evaluate(strict).built);
  });

  it("changes the multiclass result when the class order is reversed", () => {
    const strict = read("characters", "fighter-3-wizard-2.strict.json") as Strict;
    const reordered = {
      ...strict,
      [SELECTIONS]: strict[SELECTIONS].map((selection) =>
        selection[KEY] === "~:class"
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
