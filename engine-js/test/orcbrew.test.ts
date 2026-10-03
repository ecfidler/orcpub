import { readdirSync, readFileSync } from "node:fs";
import { describe, expect, it } from "vitest";
import { parseOrcbrew } from "@pubdoor/dmv";

// Each fixtures/orcbrew/<pack>.template.json records what the old importer
// returned for that pack (its "import" block) and the keys it loaded
// ("plugins"). See fixtures/README.md.
const orcbrew = new URL("../../fixtures/orcbrew/", import.meta.url);

type Fixture = {
  pack: string;
  import: Record<string, unknown>;
  plugins: Record<string, Record<string, string[]>>;
};

function text(pack: string): string {
  return readFileSync(new URL(`${pack}.orcbrew`, orcbrew), "utf8");
}

function fixture(pack: string): Fixture {
  return JSON.parse(readFileSync(new URL(`${pack}.template.json`, orcbrew), "utf8")) as Fixture;
}

const packs = readdirSync(orcbrew)
  .filter((file) => file.endsWith(".orcbrew"))
  .map((file) => file.slice(0, -".orcbrew".length))
  .sort();

/** The pack's content keys per type, as the fixture's "plugins" block lists them. */
function contentKeys(data: Record<string, object>): Record<string, Record<string, string[]>> {
  return Object.fromEntries(
    Object.entries(data).map(([pack, plugin]) => [
      pack,
      Object.fromEntries(
        Object.entries(plugin as Record<string, unknown>)
          .filter(([, items]) => items !== null && typeof items === "object" && !Array.isArray(items))
          .map(([type, items]) => [
            type.slice("~:".length),
            Object.keys(items as object)
              .map((k) => k.slice("~:".length))
              .sort(),
          ]),
      ),
    ]),
  );
}

describe("parseOrcbrew", () => {
  it("finds the fixture packs", () => {
    expect(packs.length).toBe(16);
  });

  for (const pack of packs) {
    it(`${pack} imports as the old importer did`, () => {
      const expected = fixture(pack);
      const { success, data, log, skipped } = parseOrcbrew(text(pack), { name: pack });

      expect(success).toBe(expected.import["success"]);
      expect(log.changes).toStrictEqual(expected.import["changes"]);
      expect(log["key-conflicts"]).toStrictEqual(expected.import["key-conflicts"]);
      expect(log["imported-count"]).toBe(expected.import["imported-count"]);
      expect(skipped.map(({ key, errors }) => ({ key, errors }))).toStrictEqual(
        expected.import["skipped-items"],
      );
      expect(contentKeys(data!)).toStrictEqual(expected.plugins);
    });
  }

  it("imports every drift form", () => {
    for (const pack of packs.filter((p) => p.startsWith("drift-"))) {
      expect(parseOrcbrew(text(pack), { name: pack }).success, pack).toBe(true);
    }
  });

  it("removes a leading byte-order mark", () => {
    const plain = parseOrcbrew(text("warlock-test-content"), { name: "w" });
    const bom = parseOrcbrew(`﻿${text("warlock-test-content")}`, { name: "w" });

    expect(bom.success).toBe(true);
    expect(bom.data).toStrictEqual(plain.data);
  });

  it("reports conflicts with the loaded homebrew and suggests a key", () => {
    const a = parseOrcbrew(text("duplicate-external-a"), { name: "duplicate-external-a" });
    const b = parseOrcbrew(text("duplicate-external-b"), {
      name: "duplicate-external-b",
      existing: a.data!,
    });

    expect(b.success).toBe(true);
    expect(b.conflicts).toContainEqual(
      expect.objectContaining({
        type: "external",
        key: "custom-lineage",
        "existing-source": "duplicate-external-a",
        "import-source": "duplicate-external-b",
        "suggested-new-key": "custom-lineage-duplicate-external-b",
      }),
    );
  });

  it("accepts its own data as text", () => {
    const a = parseOrcbrew(text("duplicate-external-a"), { name: "duplicate-external-a" });
    const b = parseOrcbrew(text("duplicate-external-b"), {
      name: "duplicate-external-b",
      existing: JSON.stringify(a.data),
    });

    expect(b.log["key-conflicts"]!["external-conflicts"]).not.toHaveLength(0);
  });

  it("reports a parse error with its line", () => {
    const { success, data, log } = parseOrcbrew("{:orcpub.dnd.e5/spells {:a {:name \"A\"}\n", {
      name: "broken",
    });

    expect(success).toBe(false);
    expect(data).toBeNull();
    expect(log["parse-error"]).toBe(true);
    expect(log.errors).toHaveLength(1);
  });
});
