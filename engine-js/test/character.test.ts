import { readdirSync, readFileSync } from "node:fs";
import { describe, expect, it } from "vitest";
import { exportCharacter } from "@dmv/pubdoor";

// Port of the strict-round-trip tests in
// test/cljc/orcpub/dnd/e5/character_test.clj: to-strict(from-strict(x)) = x.
// exportCharacter is exactly that, and it keeps the Datomic ids and owner,
// so the comparison is with x unchanged. import.test.ts covers the
// importCharacter path, which removes them.
const fixtures = new URL("../../fixtures/", import.meta.url);

function read(dir: string, file: string): unknown {
  return JSON.parse(readFileSync(new URL(`${dir}/${file}`, fixtures), "utf8"));
}

function roundTrips(dir: string, name: string): boolean {
  return (read(dir, `${name}.meta.json`) as { strictRoundTrip: unknown }).strictRoundTrip === true;
}

describe("character_test", () => {
  for (const [test, name] of [
    ["strict-round-trip", "character-test-1"],
    ["strict-round-trip-2", "character-test-2"],
    ["strict-round-trip-3", "character-test-3"],
  ]) {
    it(`${test}: legacy/${name}`, () => {
      const strict = read("legacy", `${name}.strict.json`) as object;

      expect(exportCharacter(strict)).toStrictEqual(strict);
    });
  }
});

describe("strict round-trip over the golden characters", () => {
  const names = readdirSync(new URL("characters/", fixtures))
    .filter((file) => file.endsWith(".strict.json"))
    .map((file) => file.slice(0, -".strict.json".length))
    .sort();

  for (const name of names) {
    const test = roundTrips("characters", name) ? it : it.skip;

    test(name, () => {
      const strict = read("characters", `${name}.strict.json`) as object;

      expect(exportCharacter(strict)).toStrictEqual(strict);
    });
  }
});
