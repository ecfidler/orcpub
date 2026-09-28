import { readdirSync, readFileSync } from "node:fs";
import { describe, expect, expectTypeOf, it } from "vitest";
import { evaluate } from "@pubdoor/dmv";
import type { Built2014, BuiltCharacter, Rules } from "@pubdoor/dmv";

const characters = new URL("../../fixtures/characters/", import.meta.url);

function fixture(name: string): unknown {
  return JSON.parse(readFileSync(new URL(name, characters), "utf8"));
}

// Every named key of the neutral part and of Built2014. The types below fail
// to compile when a list misses a key or has an extra one.
const neutralKeys = [
  "character-name", "race", "subrace", "background", "classes", "levels", "total-levels",
  "class-level", "abilities", "ability-bonuses", "proficiency-bonus", "saving-throws",
  "save-bonuses", "skill-profs", "skill-bonuses", "skill-expertise", "passive-perception",
  "initiative", "armor-profs", "weapon-profs", "tool-profs", "tool-bonus", "languages",
  "armor-class", "armor-class-with-armor", "max-hit-points", "current-hit-points",
  "base-land-speed", "base-flying-speed", "base-swimming-speed", "unarmored-speed-bonus",
  "speed-with-armor",
  "darkvision", "weapon-modifiers",
  "number-of-attacks", "attacks", "spell-slots", "spells-known", "spell-modifiers",
  "spell-save-dc", "spell-attack-modifier", "prepares-spells", "prepare-spell-count", "traits",
  "actions", "bonus-actions", "reactions", "feats", "weapons", "armor", "equipment", "treasure",
  "magic-weapons", "magic-armor", "magic-items",
] as const satisfies readonly (keyof BuiltCharacter)[];

const keys2014 = [
  "race-ability-increases", "subrace-ability-increases", "spell-slot-factors",
  "total-spellcaster-levels", "spells-known-modes", "pact-magic?", "option-sources",
  "al-illegal-reasons",
] as const;

type Unlisted = Exclude<keyof BuiltCharacter, (typeof neutralKeys)[number]>;
expectTypeOf<Unlisted>().toBeNever();

// keyof Built2014 is string through its index signature, so drop that first.
type Named<T> = keyof { [K in keyof T as string extends K ? never : K]: T[K] };
type Only2014 = Exclude<Named<Built2014>, keyof BuiltCharacter>;
expectTypeOf<Exclude<Only2014, (typeof keys2014)[number]>>().toBeNever();
expectTypeOf<Exclude<(typeof keys2014)[number], Only2014>>().toBeNever();

describe("types", () => {
  it("name keys that every golden character has", () => {
    for (const file of readdirSync(characters).filter((f) => f.endsWith(".expected.json"))) {
      const built = fixture(file) as object;
      for (const key of [...neutralKeys, ...keys2014]) expect(built, `${file} ${key}`).toHaveProperty([key]);
    }
  });

  it("keep the 2014-shaped keys off the neutral part", () => {
    const { built } = evaluate(fixture("warlock-10-drow.strict.json") as object);
    const sheet: BuiltCharacter = built;

    expectTypeOf(built["pact-magic?"]).toEqualTypeOf<boolean | null>();
    expectTypeOf(sheet["armor-class"]).toEqualTypeOf<number>();
    // @ts-expect-error race-ability-increases is only on Built2014.
    void sheet["race-ability-increases"];
    // @ts-expect-error spell-slot-factors is only on Built2014.
    void sheet["spell-slot-factors"];
    // @ts-expect-error the neutral part is not a Built2014.
    const whole: Built2014 = sheet;
    void whole;

    expect(sheet["armor-class"]).toBe(11);
    expect(built["pact-magic?"]).toBe(true);
  });

  it("export Rules as 2014 only", () => {
    expectTypeOf<Rules>().toEqualTypeOf<"2014">();
  });
});
