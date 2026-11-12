// Scaled uses and feat uses (ORC-192, patch D8). The golden suite compares
// the whole character; these tests name the cases the ticket lists.
import { readFileSync } from "node:fs";
import { describe, expect, it } from "vitest";
import { evaluate, parseOrcbrew, validate, type Feature } from "@pubdoor/dmv";

const fixtures = new URL("../../fixtures/", import.meta.url);
const text = (path: string) => readFileSync(new URL(path, fixtures), "utf8");

const { data: homebrew } = parseOrcbrew(text("orcbrew/scaled-uses.orcbrew"), { name: "scaled-uses" });
const strict = JSON.parse(text("characters/scaled-uses-fighter-6-barbarian-1.strict.json")) as object;
const { built } = evaluate(strict, { homebrew: homebrew! });

const named = (name: string): Feature[] => built.traits.filter((trait) => trait.name === name);
const uses = (name: string) => named(name)[0]?.frequency;

describe("scaled uses", () => {
  it("keeps a fixed amount", () => {
    expect(uses("Tide Step")).toEqual({ amount: 2, units: "orcpub.dnd.e5.units/short-rest" });
  });

  it("resolves an ability amount, with the minimum only when the pack has one", () => {
    // CHA 10 gives 0, and the pack's minimum is 1
    expect(uses("Brine Ward")?.amount).toBe(1);
    // INT 8 gives -1, and the pack has no minimum
    expect(uses("Undertow")?.amount).toBe(-1);
    // STR 15 gives 2, plus -1
    expect(uses("Riptide")?.amount).toBe(1);
  });

  it("resolves a proficiency bonus amount", () => {
    expect(built["proficiency-bonus"]).toBe(3);
    expect(uses("Salt Sense")?.amount).toBe(3);
    expect(uses("Ebb and Flow")?.amount).toBe(5);
  });

  it("counts the class level for a subclass feature and the character level for any other", () => {
    expect(built["class-level"]).toMatchObject({ fighter: 6, barbarian: 1 });
    // 5 x fighter level
    expect(uses("Crashing Wave")?.amount).toBe(30);
    // 2 x character level + 1
    expect(uses("Deep Lungs")?.amount).toBe(15);
    // a feat's feature: the character level
    expect(uses("Rolling Gait")?.amount).toBe(7);
  });

  it("applies a feature's level to a scaled feature", () => {
    expect(named("Tsunami")).toHaveLength(0);
  });

  it("gives a plain number for every amount", () => {
    for (const trait of built.traits) {
      if (trait.frequency) expect(typeof trait.frequency.amount).toBe("number");
    }
  });

  it("gives each feature its type", () => {
    expect(named("Tide Step")[0]?.type).toBe("b-action");
    expect(named("Brine Ward")[0]?.type).toBe("reaction");
    expect(named("Undertow")[0]?.type).toBe("action");
    expect(named("Deep Lungs")[0]?.type).toBe("other");
    expect(named("Salt Sense")[0]?.type).toBeUndefined();
  });
});

describe("feat uses", () => {
  it("gives a feat with a frequency one trait with that frequency and type", () => {
    expect(named("Stormcaller")).toEqual([
      {
        name: "Stormcaller",
        description: "You call a gust of wind that pushes one creature 10 feet.",
        type: "action",
        frequency: { amount: 3, units: "orcpub.dnd.e5.units/long-rest" },
      },
    ]);
  });

  it("puts a feat's traits in built.traits, after the feat's own trait", () => {
    const names = built.traits.map((trait) => trait.name);
    const own = names.indexOf("Sea Legs");
    expect(own).toBeGreaterThan(-1);
    expect(names.slice(own + 1, own + 3)).toEqual(["Steady Footing", "Rolling Gait"]);
    expect(named("Sea Legs")[0]?.frequency).toBeUndefined();
  });

  it("keeps the frequency, type and traits of a feat through validate.feat", () => {
    const feats = (homebrew as Record<string, Record<string, Record<string, object>>>)["scaled-uses"]![
      "~:orcpub.dnd.e5/feats"
    ]!;
    const stormcaller = validate.feat(feats["~:stormcaller"]!);
    expect(stormcaller.ok).toBe(true);
    expect(stormcaller.item).toMatchObject({
      "~:type": "~:action",
      "~:frequency": { "~:units": "~:orcpub.dnd.e5.units/long-rest", "~:amount": { "~:prof-bonus": true } },
    });
    const seaLegs = validate.feat(feats["~:sea-legs"]!);
    expect(seaLegs.ok).toBe(true);
    expect((seaLegs.item as Record<string, unknown[]>)["~:traits"]).toHaveLength(2);
  });
});
