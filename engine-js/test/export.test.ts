import { readdirSync, readFileSync } from "node:fs";
import { describe, expect, it } from "vitest";
import { orcbrewToEdn, parseOrcbrew, validateForExport } from "@pubdoor/dmv";

const orcbrew = new URL("../../fixtures/orcbrew/", import.meta.url);

const packs = readdirSync(orcbrew)
  .filter((file) => file.endsWith(".orcbrew"))
  .map((file) => file.slice(0, -".orcbrew".length))
  .sort();

function parse(pack: string) {
  const parsed = parseOrcbrew(readFileSync(new URL(`${pack}.orcbrew`, orcbrew), "utf8"), { name: pack });
  expect(parsed.success).toBe(true);
  return parsed.data!;
}

describe("orcbrewToEdn", () => {
  for (const pack of packs) {
    for (const pretty of [false, true]) {
      it(`${pack} survives export and re-import${pretty ? " pretty-printed" : ""}`, () => {
        const data = parse(pack);
        const again = parseOrcbrew(orcbrewToEdn(data, { pretty }));

        expect(again.success).toBe(true);
        expect(again.data).toStrictEqual(data);
        // The export is already clean: the old importer changes nothing.
        expect(again.log.changes).toStrictEqual([]);
        expect(again.log["skipped-items"]).toStrictEqual([]);
      });
    }

    it(`${pack} exports each pack alone as a single-plugin file`, () => {
      const data = parse(pack);
      for (const [name, plugin] of Object.entries(data)) {
        const again = parseOrcbrew(orcbrewToEdn(data, { pack: name }), { name });

        expect(again.success).toBe(true);
        expect(again.data).toStrictEqual({ [name]: plugin });
        expect(again.log.changes).toStrictEqual([]);
      }
    });

    it(`${pack} exports every item with its own key and a pack`, () => {
      for (const plugin of Object.values(parse(pack))) {
        for (const items of Object.values(plugin as Record<string, unknown>)) {
          if (items === null || typeof items !== "object") continue;
          for (const [key, item] of Object.entries(items as Record<string, Record<string, unknown>>)) {
            expect(item["~:key"]).toBe(key);
            expect(item["~:option-pack"]).toBeTruthy();
          }
        }
      }
    });
  }

  it("throws for a pack that is not loaded", () => {
    expect(() => orcbrewToEdn(parse("warlock-test-content"), { pack: "nope" })).toThrow(/No pack named "nope"/);
  });
});

describe("validateForExport", () => {
  it("passes every fixture pack", () => {
    for (const pack of packs) {
      const { valid, packs: results } = validateForExport(parse(pack));

      expect(valid, pack).toBe(true);
      for (const result of Object.values(results)) {
        expect(result.missingFields).toStrictEqual([]);
      }
    }
  });

  // An item can lose a required field after import, in the app's builders.
  const homebrew = {
    Named: { "~:orcpub.dnd.e5/feats": { "~:a": { "~:key": "~:a", "~:name": "A", "~:option-pack": "Named" } } },
    Unnamed: { "~:orcpub.dnd.e5/feats": { "~:b": { "~:key": "~:b", "~:option-pack": "Unnamed" } } },
  };

  it("reports an item without a name", () => {
    const { valid, packs: results } = validateForExport(homebrew);

    expect(valid).toBe(false);
    expect(results["Named"]!.valid).toBe(true);
    expect(results["Unnamed"]!.valid).toBe(false);
    expect(results["Unnamed"]!.missingFields).toStrictEqual([
      expect.objectContaining({
        "content-type": "orcpub.dnd.e5/feats",
        "invalid-items": [expect.objectContaining({ key: "b", "missing-fields": ["name"] })],
      }),
    ]);
  });

  it("fills the placeholders that export anyway writes", () => {
    const { filled } = validateForExport(homebrew);

    expect(filled["Unnamed"]).toStrictEqual({
      "~:orcpub.dnd.e5/feats": {
        "~:b": { "~:key": "~:b", "~:option-pack": "Unnamed", "~:name": "[Missing Feat Name]" },
      },
    });
    expect(validateForExport(filled).valid).toBe(true);
  });

  it("checks and fills only the pack named", () => {
    const { valid, packs: results, filled } = validateForExport(homebrew, { pack: "Named" });

    expect(valid).toBe(true);
    expect(Object.keys(results)).toStrictEqual(["Named"]);
    expect(filled).toStrictEqual(homebrew);
  });
});

// Patch D3: boons get the same required-field handling as the other types.
describe("boons", () => {
  const unnamed = (type: string) => `{:orcpub.dnd.e5/${type} {:x {:key :x :option-pack "P"}}}`;

  it("are filled on import like a feat", () => {
    const boon = parseOrcbrew(unnamed("boons"), { name: "P" });
    const feat = parseOrcbrew(unnamed("feats"), { name: "P" });

    expect(boon.success).toBe(true);
    expect(boon.data!["P"]).toStrictEqual({
      "~:orcpub.dnd.e5/boons": { "~:x": { "~:key": "~:x", "~:option-pack": "P", "~:name": "[Missing Boon Name]" } },
    });
    expect(JSON.stringify(boon.log.changes).replaceAll("boons", "feats")).toBe(JSON.stringify(feat.log.changes));
  });

  it("are checked before export", () => {
    const { valid, packs: results } = validateForExport({
      P: { "~:orcpub.dnd.e5/boons": { "~:x": { "~:key": "~:x", "~:option-pack": "P" } } },
    });

    expect(valid).toBe(false);
    expect(results["P"]!.missingFields).toStrictEqual([
      expect.objectContaining({ "content-type": "orcpub.dnd.e5/boons" }),
    ]);
  });
});
