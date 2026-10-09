import { readdirSync, readFileSync } from "node:fs";
import { describe, expect, it } from "vitest";
import { exportCharacter, importCharacter, readServerEdn } from "@pubdoor/dmv";

const fixtures = new URL("../../fixtures/", import.meta.url);

const DB_ID = "~:db/id";
const OWNER = "~:orcpub.entity.strict/owner";
const VALUES = "~:orcpub.entity.strict/values";
const XPS = "~:orcpub.dnd.e5.character/xps";
const KEY = "~:orcpub.entity.strict/key";

function read(dir: string, file: string): unknown {
  return JSON.parse(readFileSync(new URL(`${dir}/${file}`, fixtures), "utf8"));
}

function strictFixtures(): [dir: string, name: string][] {
  return ["characters", "legacy"].flatMap((dir) =>
    readdirSync(new URL(`${dir}/`, fixtures))
      .filter((file) => file.endsWith(".strict.json"))
      .map((file) => [dir, file.slice(0, -".strict.json".length)] as [string, string])
      .sort(),
  );
}

/** x with every old :db/id and the owner removed, as importCharacter does. */
function withoutOwnership(x: unknown): unknown {
  if (Array.isArray(x)) return x.map(withoutOwnership);
  if (x !== null && typeof x === "object") {
    return Object.fromEntries(
      Object.entries(x)
        .filter(([k]) => k !== DB_ID && k !== OWNER)
        .map(([k, v]) => [k, withoutOwnership(v)]),
    );
  }
  return x;
}

/** Every option and selection key in a strict entity, in document order. */
function keys(x: unknown): unknown[] {
  if (Array.isArray(x)) return x.flatMap(keys);
  if (x !== null && typeof x === "object") {
    return Object.entries(x).flatMap(([k, v]) => (k === KEY ? [v] : keys(v)));
  }
  return [];
}

describe("importCharacter", () => {
  for (const [dir, name] of strictFixtures()) {
    it(`imports ${dir}/${name}`, () => {
      expect(() => importCharacter(read(dir, `${name}.strict.json`) as object)).not.toThrow();
    });
  }

  it("accepts the Transit-JSON text as well as the parsed value", () => {
    const strict = read("legacy", "character-test-2.strict.json") as object;

    expect(importCharacter(JSON.stringify(strict))).toStrictEqual(importCharacter(strict));
  });

  it("removes the old ids and owner, and returns the top-level id as legacyId", () => {
    const { entity, legacyId } = importCharacter(
      read("legacy", "character-test-2.strict.json") as object,
    );

    expect(legacyId).toBe("17592186056344");
    expect(JSON.stringify(entity)).not.toContain(DB_ID);
    expect(JSON.stringify(entity)).not.toContain(OWNER);
  });

  it("returns a null legacyId for an entity without one", () => {
    expect(importCharacter(read("characters", "fighter-1.strict.json") as object).legacyId).toBeNull();
  });

  it.each([
    ["r5-xps-string", 6500],
    ["r5-xps-blank-string", 0],
  ])("parses the string xps of %s (R5)", (name, xps) => {
    const { entity } = importCharacter(read("legacy", `${name}.strict.json`) as object);

    expect((entity as Record<string, Record<string, unknown>>)[VALUES][XPS]).toBe(xps);
  });

  it("keeps unresolved keys (R8)", () => {
    const strict = read("legacy", "r8-unresolved-keys.strict.json");

    expect(keys(importCharacter(strict as object).entity)).toStrictEqual(keys(strict));
  });
});

// Where the oracle found to-strict(from-strict(x)) = x, export(import(x)) must
// give x back, less the ids and owner that import removes.
describe("exportCharacter(importCharacter(x))", () => {
  for (const [dir, name] of strictFixtures()) {
    const meta = read(dir, `${name}.meta.json`) as { strictRoundTrip: unknown };
    const test = meta.strictRoundTrip === true ? it : it.skip;

    test(`round-trips ${dir}/${name}`, () => {
      const strict = read(dir, `${name}.strict.json`);

      expect(exportCharacter(importCharacter(strict as object).entity)).toStrictEqual(
        withoutOwnership(strict),
      );
    });
  }
});

/**
 * The EDN text of a value in verbose Transit-JSON, as the old server writes
 * it: a keyword "~:k" is :k, {"~#set": [...]} is #{...}, a map key "~i1" is
 * the integer 1.
 */
function toEdn(x: unknown): string {
  if (Array.isArray(x)) return `[${x.map(toEdn).join(" ")}]`;
  if (x === null) return "nil";
  if (typeof x === "string") {
    if (x.startsWith("~:")) return `:${x.slice(2)}`;
    if (x.startsWith("~i")) return x.slice(2);
    return JSON.stringify(x.startsWith("~~") ? x.slice(1) : x);
  }
  if (typeof x === "object") {
    const entries = Object.entries(x);
    if (entries.length === 1 && entries[0][0] === "~#set") return `#{${(entries[0][1] as unknown[]).map(toEdn).join(" ")}}`;
    return `{${entries.map(([k, v]) => `${toEdn(k)} ${toEdn(v)}`).join(", ")}}`;
  }
  return String(x);
}

/** A character as GET /dnd/5e/characters/<id> returns it: EDN, with the server's tags and summary. */
function serverEdn(strict: unknown): string {
  const tags =
    ':orcpub.entity.strict/game :dnd :orcpub.entity.strict/game-version :e5 :orcpub.entity.strict/type :character ' +
    ':orcpub.entity.strict/summary {:orcpub.dnd.e5.character/character-name "Summary"}';
  return toEdn(strict).replace(/^\{/, `{${tags}, `);
}

describe("importCharacter on the old server's EDN (ORC-107)", () => {
  for (const [dir, name] of strictFixtures()) {
    it(`imports ${dir}/${name} as EDN the same as Transit-JSON`, () => {
      const strict = read(dir, `${name}.strict.json`) as object;

      expect(importCharacter(serverEdn(strict))).toStrictEqual(importCharacter(strict));
    });
  }

  it("throws for text that is neither Transit-JSON nor EDN", () => {
    expect(() => importCharacter("{:orcpub.entity.strict/selections [")).toThrow(/not Transit-JSON or EDN/);
  });
});

describe("readServerEdn", () => {
  it("gives one value per character in GET /dnd/5e/characters, and each imports as its Transit-JSON", () => {
    const strict = ["character-test-1", "character-test-2"].map((name) => read("legacy", `${name}.strict.json`) as object);
    const values = readServerEdn(`(${strict.map(serverEdn).join(" ")})`);

    expect(values).toHaveLength(2);
    values.forEach((value, i) => expect(importCharacter(value)).toStrictEqual(importCharacter(strict[i])));
  });

  it("gives an empty array for an empty list, and a one-item array for a map", () => {
    expect(readServerEdn("()")).toStrictEqual([]);
    expect(readServerEdn('{:db/id 17592186045418 :orcpub.dnd.e5.magic-items/name "Sword of Tests"}')).toStrictEqual([
      { "~:db/id": 17592186045418, "~:orcpub.dnd.e5.magic-items/name": "Sword of Tests" },
    ]);
  });

  it("throws for text that is not EDN", () => {
    expect(() => readServerEdn("(")).toThrow();
  });
});
