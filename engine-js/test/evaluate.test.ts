import { readFileSync } from "node:fs";
import { describe, expect, it } from "vitest";
import { emptyCharacter, evaluate, parseOrcbrew, select } from "@pubdoor/dmv";

const characters = new URL("../../fixtures/characters/", import.meta.url);

function fixture(name: string): unknown {
  return JSON.parse(readFileSync(new URL(name, characters), "utf8"));
}

describe("evaluate", () => {
  const strict = fixture("fighter-1.strict.json") as object;

  it("builds fighter-1 as the old app does", () => {
    const { built, selections } = evaluate(strict);

    expect(built).toEqual(fixture("fighter-1.expected.json"));
    expect(selections).toEqual(fixture("fighter-1.selections.json"));
  });

  it("accepts the Transit-JSON text as well as the parsed value", () => {
    expect(evaluate(JSON.stringify(strict))).toEqual(evaluate(strict));
  });

  it("defaults rules to 2014", () => {
    expect(evaluate(strict)).toEqual(evaluate(strict, { rules: "2014" }));
  });

  it("rejects any other rules edition", () => {
    expect(() => evaluate(strict, { rules: "2024" as never })).toThrow(/2024/);
  });
});

describe("evaluate: homebrew", () => {
  const { data: homebrew } = parseOrcbrew(
    readFileSync(new URL("../orcbrew/duplicate-external-b.orcbrew", characters), "utf8"),
    { name: "duplicate-external-b" },
  );
  const strict = fixture("ironwrought-artificer-3.strict.json") as object;

  it("builds a homebrew character with its pack", () => {
    const { built } = evaluate(strict, { homebrew: homebrew! });

    expect(built).toEqual(fixture("ironwrought-artificer-3.expected.json"));
  });

  it("builds against the SRD only without the pack", () => {
    expect((evaluate(strict).built as Record<string, unknown>)["race"]).toBeNull();
  });

  it("accepts the homebrew as text", () => {
    expect(evaluate(strict, { homebrew: JSON.stringify(homebrew) })).toEqual(
      evaluate(strict, { homebrew: homebrew! }),
    );
  });

  it("returns the same object for the same entity and homebrew", () => {
    const first = evaluate(strict, { homebrew: homebrew! });

    expect(evaluate(strict, { homebrew: structuredClone(homebrew!) })).toBe(first);
    expect(evaluate(strict)).not.toBe(first);
  });

  it("lets the mutations select homebrew options", () => {
    const entity = select(emptyCharacter(), ["race"], "ironwrought", { homebrew: homebrew! });

    expect((evaluate(entity, { homebrew: homebrew! }).built as Record<string, unknown>)["race"]).toBe(
      "Ironwrought",
    );
    expect(() => select(emptyCharacter(), ["race"], "ironwrought")).toThrow();
  });
});

describe("evaluate: the hand slots Dueling reads", () => {
  // fighter-5 has Dueling, a longsword in the main hand and a shield in the
  // off hand.
  const fighter5 = fixture("fighter-5.strict.json") as {
    "~:orcpub.entity.strict/values": Record<string, unknown>;
  };
  const offHand = "~:orcpub.dnd.e5.character/off-hand-weapon";

  function longswordDamage(offHandValue: string | undefined): unknown {
    const values = { ...fighter5["~:orcpub.entity.strict/values"] };
    if (offHandValue === undefined) delete values[offHand];
    else values[offHand] = offHandValue;
    const { built } = evaluate({ ...fighter5, "~:orcpub.entity.strict/values": values });
    const modifiers = built["weapon-modifiers"] as Record<string, { "best-damage": number }>;
    return modifiers["longsword"]!["best-damage"];
  }

  it("adds +2 with a non-weapon in the off hand", () => {
    expect(longswordDamage("~:shield")).toBe(6);
  });

  it("adds nothing with an empty off hand", () => {
    expect(longswordDamage(undefined)).toBe(4);
  });

  it("adds nothing with a weapon in the off hand", () => {
    expect(longswordDamage("~:handaxe")).toBe(4);
  });
});
