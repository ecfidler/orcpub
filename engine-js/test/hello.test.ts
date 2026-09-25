import { describe, expect, it } from "vitest";
import { hello } from "@dmv/pubdoor";

describe("hello", () => {
  it("returns a plain JS object built from engine data", () => {
    const result = hello();

    expect(result).toEqual({
      greeting: "hello",
      abilities: ["str", "dex", "con", "int", "wis", "cha"],
    });
    expect(Object.getPrototypeOf(result)).toBe(Object.prototype);
    expect(Array.isArray(result.abilities)).toBe(true);
  });
});
