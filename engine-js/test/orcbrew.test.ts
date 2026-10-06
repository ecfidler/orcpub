import { readdirSync, readFileSync } from "node:fs";
import { describe, expect, it } from "vitest";
import { parseOrcbrew } from "@pubdoor/dmv";
import { contentKeys } from "./support.js";

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

describe("parseOrcbrew", () => {
  it("finds the fixture packs", () => {
    expect(packs.length).toBe(18);
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
    const bom = parseOrcbrew(`\uFEFF${text("warlock-test-content")}`, { name: "w" });

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

  it("adds a single-plugin file to the loaded packs, replacing the pack under its name", () => {
    const a = parseOrcbrew(text("duplicate-external-a"), { name: "duplicate-external-a" });
    const b = parseOrcbrew(text("duplicate-external-b"), { name: "duplicate-external-b", existing: a.data! });
    const again = parseOrcbrew(text("warlock-test-content"), { name: "duplicate-external-a", existing: b.data! });

    expect(Object.keys(b.data!).sort()).toEqual(["duplicate-external-a", "duplicate-external-b"]);
    expect(contentKeys(again.data!)["duplicate-external-a"]).toStrictEqual(
      fixture("warlock-test-content").plugins["warlock-test-content"],
    );
  });

  it("merges a multi-plugin file into same-named packs by content type", () => {
    const pack = (key: string) =>
      `{"Shared" {:orcpub.dnd.e5/languages {:${key} {:name "${key}" :key :${key} :option-pack "Shared"}}}}`;
    const first = parseOrcbrew(pack("first"));
    const second = parseOrcbrew(pack("second"), { existing: first.data! });

    expect(second.success).toBe(true);
    expect(contentKeys(second.data!)).toStrictEqual({
      Shared: { "orcpub.dnd.e5/languages": ["first", "second"] },
    });
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

  // Linear ORC-116: these threw a ClojureScript protocol error in 0.2.0.
  it("skips a pack that is not a map", () => {
    const { success, data, log, skipped } = parseOrcbrew(
      '{"bad" "text" "good" {:orcpub.dnd.e5/languages {:a {:name "A" :key :a :option-pack "good"}}}}',
    );

    expect(success).toBe(true);
    expect(Object.keys(data!)).toEqual(["good"]);
    expect(skipped).toStrictEqual([{ key: "bad", errors: ["The pack is not a map"] }]);
    expect(log["skipped-count"]).toBe(1);
  });

  it("fails when no pack is a map", () => {
    for (const input of ['{"bad" "text"}', '{"bad" 5 "worse" :x}']) {
      const { success, data, log } = parseOrcbrew(input);

      expect(success, input).toBe(false);
      expect(data, input).toBeNull();
      expect(log.errors, input).toStrictEqual(["No pack in the file is a map"]);
    }
  });

  it("skips an item that is not a map", () => {
    const multi = parseOrcbrew('{"bad" {:orcpub.dnd.e5/spells {:x 5}}}');
    const single = parseOrcbrew('{:orcpub.dnd.e5/spells {:x 5 :y "s"}}');

    expect(multi.success).toBe(true);
    expect(multi.skipped.map(({ key }) => key)).toEqual(["x"]);
    expect(single.success).toBe(true);
    expect(single.skipped.map(({ key }) => key).sort()).toEqual(["x", "y"]);
  });

  it("fails on a file that is not a map", () => {
    const { success, data } = parseOrcbrew("[1 2]");

    expect(success).toBe(false);
    expect(data).toBeNull();
  });
});
