import { readFileSync } from "node:fs";
import { describe, expect, it } from "vitest";
import { evaluate, parseOrcbrew } from "@pubdoor/dmv";

// Port of test/cljc/orcpub/dnd/e5/warlock_test.clj. The character needs
// warlock-test-content.orcbrew for the Drow subrace, the Spy background and
// the Keen Mind feat, which warlock_test.clj defines inline.
const strict = JSON.parse(
  readFileSync(
    new URL("../../fixtures/characters/warlock-10-drow.strict.json", import.meta.url),
    "utf8",
  ),
) as object;

const { data: homebrew } = parseOrcbrew(
  readFileSync(
    new URL("../../fixtures/orcbrew/warlock-test-content.orcbrew", import.meta.url),
    "utf8",
  ),
  { name: "warlock-test-content" },
);

type SpellsKnown = Record<string, { __entries: [[string, string], unknown][] }>;

function hasSpell(
  built: Record<string, unknown>,
  level: number,
  className: string,
  spell: string,
): boolean {
  const entries = (built["spells-known"] as SpellsKnown)[level]?.__entries ?? [];
  return entries.some(([[c, s]]) => c === className && s === spell);
}

describe("warlock_test", () => {
  const built = evaluate(strict, { homebrew: homebrew! }).built as Record<string, unknown>;

  it("build-smoke-test: builds without throwing", () => {
    expect(built).toBeTruthy();
  });

  it("warlock-class-levels: warlock has 10 levels", () => {
    expect(built["levels"]).toMatchObject({ warlock: { "class-level": 10 } });
  });

  it("warlock-race-and-subrace: race is Elf", () => {
    expect(built["race"]).toBe("Elf");
  });

  it("warlock-speed: elf base speed is 30", () => {
    expect(built["base-land-speed"]).toBe(30);
  });

  it("warlock-spells: invocations and pacts add spells", () => {
    // Book of Ancient Secrets ritual
    expect(hasSpell(built, 1, "Warlock", "illusory-script")).toBe(true);
    // Book of Shadows cantrip
    expect(hasSpell(built, 0, "Warlock", "spare-the-dying")).toBe(true);
    // Beast Speech invocation
    expect(hasSpell(built, 1, "Warlock", "speak-with-animals")).toBe(true);
  });

  it("warlock-ability-scores: Keen Mind and the Drow subrace raise INT and CHA", () => {
    expect(built["abilities"]).toStrictEqual({
      "orcpub.dnd.e5.character/str": 10,
      // 11 base + 2 elf
      "orcpub.dnd.e5.character/dex": 13,
      "orcpub.dnd.e5.character/con": 11,
      // 15 base + 1 Keen Mind
      "orcpub.dnd.e5.character/int": 16,
      "orcpub.dnd.e5.character/wis": 14,
      // 15 base + 1 Drow
      "orcpub.dnd.e5.character/cha": 16,
    });
  });

  it("warlock-race-and-subrace: subrace is Dark Elf (Drow)", () => {
    expect(built["subrace"]).toBe("Dark Elf (Drow)");
  });

  it("warlock-skill-proficiencies: elf, Spy and warlock skills, five in all", () => {
    expect(Object.keys(built["skill-profs"] as object).sort()).toEqual([
      "deception",
      "history",
      "intimidation",
      "perception",
      "stealth",
    ]);
  });
});
