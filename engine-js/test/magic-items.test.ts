// Custom magic items (ORC-126): the magicItems option gives the template a
// user's items from the old server's GET /dnd/5e/items, as the old app gave
// that response to its template. The golden fixture
// fighter-5-custom-magic-items checks the built values against the oracle;
// these tests check the option itself.
import { describe, expect, it } from "vitest";
import {
  addInventoryItem,
  buildTemplate,
  evaluate,
  keys,
  readServerEdn,
  reconcileMissingContent,
  type MagicItems,
} from "@pubdoor/dmv";
import { readFixture, readFixtureText } from "./support.js";

const itemsText = readFixtureText("magic-items/custom-items.edn");
const magicItems = readServerEdn(itemsText);
const fighter = readFixture("characters/fighter-5.strict.json") as object;
const equipped = readFixture("characters/fighter-5-custom-magic-items.strict.json") as object;

function wisdom(entity: object, items?: MagicItems): number {
  return evaluate(entity, { magicItems: items }).built.abilities["orcpub.dnd.e5.character/wis"];
}

function optionKeys(selection: string, items?: MagicItems): string[] {
  const entry = buildTemplate(undefined, { magicItems: items }).summary.find((s) => s.key === selection);
  return entry!.optionKeys;
}

describe("magicItems", () => {
  it("lists each custom item in its magic item selection, a weapon once per base weapon", () => {
    expect(optionKeys("magic-weapons", magicItems)).toEqual(
      expect.arrayContaining([
        "emberbrand-greatsword",
        "emberbrand-longsword",
        "emberbrand-rapier",
        "emberbrand-scimitar",
        "emberbrand-shortsword",
      ]),
    );
    expect(optionKeys("magic-armor", magicItems)).toContain("wardens-plate");
    expect(optionKeys("other-magic-items", magicItems)).toEqual(
      expect.arrayContaining(["circlet-of-the-hawk", "pearl-of-stillwater"]),
    );
  });

  it("adds only the custom items to the SRD template", () => {
    const srd = keys.optionKeys();
    const added = keys.optionKeys(undefined, { magicItems }).filter((k) => !srd.includes(k));

    expect(added.sort()).toEqual([
      "circlet-of-the-hawk",
      "emberbrand-greatsword",
      "emberbrand-longsword",
      "emberbrand-rapier",
      "emberbrand-scimitar",
      "emberbrand-shortsword",
      "pearl-of-stillwater",
      "wardens-plate",
    ]);
  });

  it("applies an equipped item's modifiers to a character that the app gives the item", () => {
    const withCirclet = addInventoryItem(fighter, "other-magic-items", "circlet-of-the-hawk");

    expect(wisdom(withCirclet, magicItems)).toBe(wisdom(fighter) + 2);
    expect(wisdom(withCirclet)).toBe(wisdom(fighter));
  });

  it("accepts the JSON text of the items as well as the array", () => {
    expect(evaluate(equipped, { magicItems: JSON.stringify(magicItems) })).toStrictEqual(
      evaluate(equipped, { magicItems }),
    );
  });

  it("builds the template again when the items change", () => {
    const [weapon, ...others] = magicItems;

    expect(optionKeys("magic-weapons", others)).not.toContain("emberbrand-longsword");
    expect(optionKeys("magic-weapons", [weapon, ...others])).toContain("emberbrand-longsword");
  });

  it("leaves a carried custom item unresolved without the items", () => {
    expect(reconcileMissingContent(equipped, undefined, { magicItems }).hasMissing).toBe(false);

    const report = reconcileMissingContent(equipped);
    expect(report.items).toEqual([]);
    expect(report.unresolvedOptions.map((o) => o.path.join("/")).sort()).toEqual([
      "magic-armor/wardens-plate",
      "magic-weapons/emberbrand-greatsword",
      "magic-weapons/emberbrand-longsword",
      "other-magic-items/circlet-of-the-hawk",
      "other-magic-items/pearl-of-stillwater",
    ]);
  });

  it("throws unless the items are an array of maps", () => {
    expect(() => evaluate(fighter, { magicItems: "{}" })).toThrow(/magicItems/);
    expect(() => evaluate(fighter, { magicItems: [1] as unknown as object[] })).toThrow(/magicItems/);
    expect(() => buildTemplate(undefined, { magicItems: "{}" })).toThrow(/magicItems/);
  });
});
