import { readFileSync } from "node:fs";
import { gunzipSync } from "node:zlib";
import { describe, expect, it } from "vitest";
import { buildTemplate, keys, parseOrcbrew, type TemplateSelection } from "@pubdoor/dmv";

const orcbrew = new URL("../../fixtures/orcbrew/", import.meta.url);

/** The distinct selection and option keys of a template shape, sorted. */
function keySets(shape: TemplateSelection[]): { selectionKeys: string[]; optionKeys: string[] } {
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

describe("keys", () => {
  // The C3 content-identity baseline: every built-in selection and option key.
  const baseline = keySets(
    (
      JSON.parse(
        gunzipSync(readFileSync(new URL("_srd-baseline.template.json.gz", orcbrew))).toString("utf8"),
      ) as { shape: TemplateSelection[] }
    ).shape,
  );

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

  it("defaults to the SRD", () => {
    expect(keys.selectionKeys()).toStrictEqual(keys.selectionKeys({}));
    expect(keys.optionKeys()).toStrictEqual(keys.optionKeys({}));
  });

  it("walks the template for homebrew", () => {
    const pack = "community-mezzoloth-race";
    const { data } = parseOrcbrew(readFileSync(new URL(`${pack}.orcbrew`, orcbrew), "utf8"), { name: pack });
    const expected = keySets(buildTemplate(data!).shape);

    expect(keys.selectionKeys(data!)).toStrictEqual(expected.selectionKeys);
    expect(keys.optionKeys(data!)).toStrictEqual(expected.optionKeys);
    expect(keys.optionKeys(data!)).toContain("mezzoloth");
    expect(keys.optionKeys()).not.toContain("mezzoloth");
  });
});
