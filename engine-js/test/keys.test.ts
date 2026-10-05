import { describe, expect, it } from "vitest";
import { buildTemplate, keys, parseOrcbrew } from "@pubdoor/dmv";
import { keySets, readFixtureText } from "./support.js";

// content-identity.test.ts compares the SRD key sets with the baseline.
describe("keys", () => {
  it("defaults to the SRD", () => {
    expect(keys.selectionKeys()).toStrictEqual(keys.selectionKeys({}));
    expect(keys.optionKeys()).toStrictEqual(keys.optionKeys({}));
  });

  it("walks the template for homebrew", () => {
    const pack = "community-mezzoloth-race";
    const { data } = parseOrcbrew(readFixtureText(`orcbrew/${pack}.orcbrew`), { name: pack });
    const expected = keySets(buildTemplate(data!).shape);

    expect(keys.selectionKeys(data!)).toStrictEqual(expected.selectionKeys);
    expect(keys.optionKeys(data!)).toStrictEqual(expected.optionKeys);
    expect(keys.optionKeys(data!)).toContain("mezzoloth");
    expect(keys.optionKeys()).not.toContain("mezzoloth");
  });
});
