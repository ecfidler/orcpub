import { readFileSync } from "node:fs";
import { describe, expect, it } from "vitest";
import { evaluate } from "@dmv/pubdoor";

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
