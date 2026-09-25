import { readFileSync } from "node:fs";
import { describe, expect, it } from "vitest";
import { evaluate } from "@dmv/pubdoor";

// Port of test/cljc/orcpub/dnd/e5/warlock_test.clj. The character needs
// warlock-test-content.orcbrew for the Drow subrace, the Spy background and
// the Keen Mind feat. Until buildTemplate (ORC-27) loads it, evaluate builds
// against the SRD only, so the checks that depend on the pack are todo.
const strict = JSON.parse(
  readFileSync(
    new URL("../../fixtures/characters/warlock-10-drow.strict.json", import.meta.url),
    "utf8",
  ),
) as object;

type SpellsKnown = Record<string, { __entries: [[string, string], unknown][] }>;

function hasSpell(built: Record<string, unknown>, level: number, className: string, spell: string) {
  const entries = (built["spells-known"] as SpellsKnown)[level]?.__entries ?? [];
  return entries.some(([[c, s]]) => c === className && s === spell);
}

describe("warlock_test", () => {
  const built = () => evaluate(strict).built as Record<string, unknown>;

  it("build-smoke-test: builds without throwing", () => {
    expect(built()).toBeTruthy();
  });

  it("warlock-class-levels: warlock has 10 levels", () => {
    expect(built()["levels"]).toMatchObject({ warlock: { "class-level": 10 } });
  });

  it("warlock-race-and-subrace: race is Elf", () => {
    expect(built()["race"]).toBe("Elf");
  });

  it("warlock-speed: elf base speed is 30", () => {
    expect(built()["base-land-speed"]).toBe(30);
  });

  it("warlock-spells: invocations and pacts add spells", () => {
    // Book of Ancient Secrets ritual
    expect(hasSpell(built(), 1, "Warlock", "illusory-script")).toBe(true);
    // Book of Shadows cantrip
    expect(hasSpell(built(), 0, "Warlock", "spare-the-dying")).toBe(true);
    // Beast Speech invocation
    expect(hasSpell(built(), 1, "Warlock", "speak-with-animals")).toBe(true);
  });

  it.todo("warlock-ability-scores: INT 16 and CHA 16 need Keen Mind and Drow (ORC-27, buildTemplate)");
  it.todo("warlock-race-and-subrace: subrace Dark Elf (Drow) needs the pack (ORC-27, buildTemplate)");
  it.todo("warlock-skill-proficiencies: Spy's deception and stealth need the pack (ORC-27, buildTemplate)");
});
