import { describe, expect, it } from "vitest";
import { addLevel, autofill, emptyCharacter, evaluate, exportCharacter, select, setValue } from "@dmv/pubdoor";
import type { Selection } from "@dmv/pubdoor";

const VALUES = "~:orcpub.entity.strict/values";
const SEEDS = [1, 2, 3, 4, 5, 6, 7, 8, 9, 10];

type Node = Record<string, unknown>;

/**
 * The selections with picks remaining, counted as the builder counts them.
 * evaluate reports each occurrence of a ref selection, such as languages,
 * on its own, and each occurrence's remaining counts every pick at the
 * shared path, so a filled ref selection reports negative numbers there
 * (fixtures/characters/fighter-1.selections.json does too).
 * entity/combine-selections sums min and max across the occurrences.
 */
function unfilled(entity: object): string[] {
  const groups = new Map<string, Selection[]>();
  for (const s of evaluate(entity).selections) {
    const k = JSON.stringify(s.actualPath);
    groups.set(k, [...(groups.get(k) ?? []), s]);
  }
  const result: string[] = [];
  for (const [path, group] of groups) {
    let remaining = group[0]!.remaining;
    if (group[0]!.ref !== undefined) {
      const count = group[0]!.selected.length;
      const min = group.reduce((n, s) => n + (s.min ?? 0), 0);
      const max = group.every((s) => s.max !== null) ? group.reduce((n, s) => n + s.max!, 0) : null;
      remaining = count < min ? min - count : max !== null && count > max ? max - count : 0;
    }
    if (remaining !== 0) result.push(`${path} remaining ${remaining}`);
  }
  return result;
}

const cache = new Map<number, object>();

/** autofill(emptyCharacter(), { seed }), computed once per seed. */
function filledEmpty(seed: number): object {
  if (!cache.has(seed)) cache.set(seed, autofill(emptyCharacter(), { seed }));
  return cache.get(seed)!;
}

/** The keys selected at the top-level selection key, such as "class". */
function selected(entity: object, key: string): unknown[] {
  return evaluate(entity).selections.find((s) => s.key === key)!.selected;
}

// Each autofill builds the character many times, which takes about a second.
describe("autofill", { timeout: 60_000 }, () => {
  for (const seed of SEEDS) {
    it(`fills every selection of emptyCharacter with seed ${seed}`, () => {
      expect(unfilled(filledEmpty(seed))).toEqual([]);
    });

    it(`round-trips through exportCharacter with seed ${seed}`, () => {
      const filled = filledEmpty(seed);
      expect(exportCharacter(filled)).toEqual(filled);
    });
  }

  it("returns the same entity for the same seed", () => {
    expect(autofill(emptyCharacter(), { seed: 1 })).toEqual(filledEmpty(1));
  });

  it("returns different characters for different seeds", () => {
    const entities = new Set(SEEDS.map((seed) => JSON.stringify(filledEmpty(seed))));
    expect(entities.size).toBe(SEEDS.length);
  });

  it("replaces the unkept choices, as the old random button does", () => {
    const classes = new Set(SEEDS.map((seed) => JSON.stringify(selected(filledEmpty(seed), "class"))));
    expect(classes.size).toBeGreaterThan(1);
  });

  it("works without a seed", () => {
    expect(unfilled(autofill(emptyCharacter()))).toEqual([]);
  });

  it("keeps the selections at the keep paths", () => {
    const dwarf = select(emptyCharacter(), ["race"], "dwarf");
    for (const seed of SEEDS) {
      const filled = autofill(dwarf, { seed, keep: [["race"]] });
      expect(selected(filled, "race")).toEqual(["dwarf"]);
      expect(unfilled(filled)).toEqual([]);
    }
  });

  it("keeps the enabled plugins, :optional-content, as the old button does", () => {
    const SELECTIONS = "~:orcpub.entity.strict/selections";
    const start = emptyCharacter() as Node;
    const plugins = {
      "~:orcpub.entity.strict/key": "~:optional-content",
      "~:orcpub.entity.strict/options": [{ "~:orcpub.entity.strict/key": "~:my-plugin" }],
    };
    const withPlugins = { ...start, [SELECTIONS]: [...(start[SELECTIONS] as unknown[]), plugins] };
    const kept = (autofill(withPlugins, { seed: 1 }) as Node)[SELECTIONS] as Node[];
    expect(kept.filter((s) => s["~:orcpub.entity.strict/key"] === "~:optional-content")).toEqual([plugins]);
  });

  it("drops the values unless keepAll is set", () => {
    const named = setValue(emptyCharacter(), "character-name", "Brak");
    const name = (e: object) => ((e as Node)[VALUES] as Node | undefined)?.["~:orcpub.dnd.e5.character/character-name"];
    expect(name(autofill(named, { seed: 1 }))).toBeUndefined();
    expect(name(autofill(named, { seed: 1, keepAll: true }))).toBe("Brak");
  });

  it("with keepAll, keeps every choice and fills the rest", () => {
    const start = emptyCharacter();
    for (const seed of SEEDS) {
      const filled = autofill(start, { seed, keepAll: true });
      expect(selected(filled, "class")).toEqual(["barbarian"]);
      expect(unfilled(filled)).toEqual([]);
    }
  });

  it("backtracks from a feat when no feat is left, but never from the entity's own choices", () => {
    // The SRD has one feat. A barbarian 8 who picked a feat at levels 4 and 8
    // can fill only one of them.
    let barbarian: object = emptyCharacter();
    for (let level = 2; level <= 8; level++) barbarian = addLevel(barbarian, "barbarian");
    for (const level of [4, 8]) {
      barbarian = select(barbarian, ["class", "barbarian", "levels", `level-${level}`, "asi-or-feat"], "feat");
    }
    const asiOrFeat = (e: object) =>
      evaluate(e)
        .selections.filter((s) => s.key === "asi-or-feat")
        .map((s) => s.selected);
    for (const seed of [1, 2, 3]) {
      const filled = autofill(barbarian, { seed, keepAll: true });
      expect(asiOrFeat(filled)).toEqual([["feat"], ["feat"]]);
      expect(unfilled(filled)).toEqual(['["feats"] remaining 1']);
    }
  });

  it("rejects a seed that is not a 32-bit integer", () => {
    expect(() => autofill(emptyCharacter(), { seed: 1.5 })).toThrow(/seed/);
  });
});
