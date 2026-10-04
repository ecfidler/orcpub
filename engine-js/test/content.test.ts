import { readFileSync } from "node:fs";
import { createRequire } from "node:module";
import { fileURLToPath } from "node:url";
import { gunzipSync } from "node:zlib";
import { describe, expect, it } from "vitest";
import type { ContentItem, ContentLists, TemplateSummaryEntry } from "@pubdoor/dmv";

// `npm run build` writes dist/content/<list>.json (orcpub.facade.content).
const content = new URL("../dist/content/", import.meta.url);
const orcbrew = new URL("../../fixtures/orcbrew/", import.meta.url);

function list<K extends keyof ContentLists>(name: K): ContentLists[K] {
  return JSON.parse(readFileSync(new URL(`${name}.json`, content), "utf8")) as ContentLists[K];
}

const keysOf = (items: ContentItem[]): string[] => items.map((item) => item.key);

// The plan (orc-alchemy docs/plan/06-milestones-and-risks.md) gives 12
// classes, 9 races, 268 spells, and 288 magic items. The classes and races
// match. The spells and magic items are counted from the data: spells.cljc
// has 319 spells, all SRD 5.1. raw-magic-items in magic_items.cljc has 337
// entries, more than its 259 literal names because helpers such as
// armors-of-resistance generate some. They expand to 805 once each weapon and
// armor item becomes one item per base weapon or armor, as the builder lists
// them.
const counts: Record<keyof ContentLists, number> = {
  classes: 12,
  races: 9,
  backgrounds: 1,
  feats: 1,
  languages: 16,
  spells: 319,
  monsters: 317,
  "magic-items": 805,
  weapons: 40,
  ammunition: 5,
  armor: 14,
  equipment: 162,
  treasure: 11,
};

describe("content lists", () => {
  const summary = new Map(
    (
      JSON.parse(
        gunzipSync(readFileSync(new URL("_srd-baseline.template.json.gz", orcbrew))).toString("utf8"),
      ) as { summary: TemplateSummaryEntry[] }
    ).summary.map((s) => [s.key, s.optionKeys]),
  );
  /** The SRD template's option keys for a top-level selection, without "custom". */
  const templateKeys = (...selections: string[]): string[] =>
    selections.flatMap((s) => summary.get(s)!).filter((k) => k !== "custom");

  for (const [name, count] of Object.entries(counts) as [keyof ContentLists, number][]) {
    it(`${name}.json has ${count} items, each with a unique key and a name`, () => {
      const items = list(name);

      expect(items).toHaveLength(count);
      expect(new Set(keysOf(items)).size).toBe(count);
      for (const item of items) {
        expect(typeof item.key).toBe("string");
        expect(typeof item.name).toBe("string");
      }
    });
  }

  it("resolves through the package exports", () => {
    const require = createRequire(import.meta.url);

    expect(require.resolve("@pubdoor/dmv/content/monsters.json")).toBe(
      fileURLToPath(new URL("monsters.json", content)),
    );
  });

  it("lists what the SRD template offers", () => {
    const expected: [keyof ContentLists, string[]][] = [
      ["classes", templateKeys("class")],
      ["races", templateKeys("race")],
      ["backgrounds", templateKeys("background")],
      ["feats", templateKeys("feats")],
      ["weapons", templateKeys("weapons")],
      ["armor", templateKeys("armor")],
      ["equipment", templateKeys("equipment")],
      ["treasure", templateKeys("treasure")],
      ["magic-items", templateKeys("magic-weapons", "magic-armor", "other-magic-items")],
    ];
    for (const [name, keys] of expected) {
      expect(keysOf(list(name)).sort(), name).toStrictEqual([...keys].sort());
    }
    expect(keysOf(list("classes")), "template order").toStrictEqual(templateKeys("class"));
  });

  it("converts items to plain data", () => {
    const aboleth = list("monsters").find((m) => m.key === "aboleth")!;
    const acidArrow = list("spells").find((s) => s.key === "acid-arrow")!;
    const dwarf = list("races").find((r) => r.key === "dwarf")!;
    const flameTongue = list("magic-items").find((i) => i.key === "flame-tongue-longsword")!;

    expect(aboleth).toMatchObject({
      name: "Aboleth",
      size: "large",
      type: "aberration",
      challenge: 10,
      "hit-points": { mean: 135, "die-count": 18, die: 10, modifier: 36 },
    });
    expect(list("monsters").find((m) => m.key === "kobold")!.challenge).toBe(0.125);
    expect(acidArrow).toMatchObject({ name: "Acid Arrow", level: 2, school: "evocation" });
    expect(acidArrow.components).toMatchObject({ verbal: true, somatic: true, material: true });
    expect(dwarf).toMatchObject({ name: "Dwarf", speed: 25, abilities: { "orcpub.dnd.e5.character/con": 2 } });
    expect(dwarf).not.toHaveProperty("modifiers");
    expect(dwarf).not.toHaveProperty("selections");
    expect(flameTongue).toMatchObject({
      name: "Flame Tongue, Longsword",
      "orcpub.dnd.e5.magic-items/name": "Flame Tongue",
      "base-key": "longsword",
    });
    expect(list("backgrounds")[0]).toMatchObject({ key: "acolyte", name: "Acolyte" });
  });

  it("types the spell and monster fields", () => {
    for (const spell of list("spells")) {
      expect(typeof spell.level).toBe("number");
      for (const field of ["school", "casting-time", "range", "duration", "description"] as const) {
        expect(typeof spell[field]).toBe("string");
      }
      expect(typeof spell.components).toBe("object");
    }
    for (const monster of list("monsters")) {
      for (const field of ["size", "type", "alignment"] as const) {
        expect(typeof monster[field]).toBe("string");
      }
      for (const field of ["challenge", "armor-class", "str", "dex", "con", "int", "wis", "cha"] as const) {
        expect(typeof monster[field]).toBe("number");
      }
      expect(typeof monster["hit-points"]).toBe("object");
    }
  });
});
