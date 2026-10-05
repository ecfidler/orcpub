import { existsSync, readFileSync, statSync } from "node:fs";
import { beforeAll, describe, expect, it } from "vitest";
import { buildTemplate, parseOrcbrew, type BuiltTemplate, type ParsedOrcbrew } from "@pubdoor/dmv";
import { contentKeys, fixtures, readFixture } from "./support.js";

// The real-export regression (Linear ORC-39): the repo owner's 29-pack
// all-content.orcbrew must import as the old importer did and build the
// same template. The export is WotC text and cannot be committed, so it is
// git-ignored and this suite is skipped unless it is present. Its keys-only
// summary is committed: scripts/dump-template.clj --summary wrote it with
// the JVM oracle. See fixtures/README.md, "Private exports".

const exportFile = new URL("orcbrew/private/all-content3.orcbrew", fixtures);
const exportPresent = existsSync(exportFile);

interface Summary {
  pack: string;
  bomStripped: boolean;
  bytes: number;
  import: {
    success: boolean;
    changes: Record<string, unknown>[];
    "imported-count": number;
    "skipped-count": number;
    "skipped-items": unknown[];
    "key-conflicts": { "internal-conflicts": object[]; "external-conflicts": object[] };
  };
  plugins: Record<string, Record<string, string[]>>;
  content: Record<string, string[]>;
  templateSummary: BuiltTemplate["summary"];
}

// The change types the importer gained after the summary was written:
// ORC-40's ability-key rewrite and ORC-39's default :choose. They are left
// out on both sides, so the suite passes whether or not the summary has been
// regenerated since.
const newChangeTypes = new Set(["normalized-ability-key", "defaulted-choose"]);

// The JVM and the browser iterate small sets and some hash maps in
// different orders (fixtures/README.md findings 13 and 15). The lists below
// come from iterating the packs' maps, so they are compared as sorted lists.
const sorted = <T>(xs: T[]): T[] => [...xs].sort();
const sortedJson = (xs: unknown[]): string[] => xs.map((x) => JSON.stringify(x)).sort();

describe.skipIf(!exportPresent)("real export: private/all-content3.orcbrew (ORC-39)", () => {
  const summary = readFixture("orcbrew/private/all-content3.summary.json") as Summary;
  let parsed: ParsedOrcbrew;
  let template: BuiltTemplate;

  beforeAll(() => {
    parsed = parseOrcbrew(readFileSync(exportFile, "utf8"), { name: summary.pack });
    template = buildTemplate(parsed.data!);
  }, 300_000);

  it("is the export the summary was made from", () => {
    const text = readFileSync(exportFile, "utf8");

    expect(statSync(exportFile).size).toBe(summary.bytes);
    expect(text.startsWith("\uFEFF")).toBe(summary.bomStripped);
  });

  it("imports with nothing skipped", () => {
    const { log, skipped } = parsed;

    expect(parsed.success).toBe(summary.import.success);
    expect(log["imported-count"]).toBe(summary.import["imported-count"]);
    expect(log["skipped-count"]).toBe(summary.import["skipped-count"]);
    expect(skipped.map(({ key, errors }) => ({ key, errors }))).toStrictEqual(summary.import["skipped-items"]);
  });

  it("reports the same key conflicts", () => {
    const conflicts = parsed.log["key-conflicts"]!;
    const expected = summary.import["key-conflicts"];

    expect(expected["internal-conflicts"]).toHaveLength(86);
    expect(conflicts["internal-conflicts"]).toHaveLength(expected["internal-conflicts"].length);
    expect(sortedJson(conflicts["internal-conflicts"])).toStrictEqual(sortedJson(expected["internal-conflicts"]));
    expect(conflicts["external-conflicts"]).toStrictEqual(expected["external-conflicts"]);
  });

  it("makes the same auto-clean changes", () => {
    const oldImporterChanges = (changes: object[]) =>
      sortedJson((changes as Record<string, unknown>[]).filter((c) => !newChangeTypes.has(c["type"] as string)));

    expect(oldImporterChanges(parsed.log.changes)).toStrictEqual(oldImporterChanges(summary.import.changes));
  });

  // fixtures/README.md finding 9: four subclasses in this export have
  // :skill-options without :choose.
  it("defaults :choose on the export's skill choices that lack it", () => {
    const skillChoices = ["~:skill-options", "~:multiclass-skill-options"];
    const missing = Object.values(parsed.data!).flatMap((plugin) =>
      Object.values(plugin as Record<string, unknown>)
        .filter((items): items is Record<string, Record<string, unknown>> => !!items && typeof items === "object")
        .flatMap((items) => Object.entries(items))
        .filter(([, item]) => {
          const profs = item?.["~:profs"] as Record<string, Record<string, unknown>> | undefined;
          return skillChoices.some(
            (field) => profs?.[field]?.["~:options"] != null && profs[field]!["~:choose"] == null,
          );
        })
        .map(([key]) => key),
    );
    const defaulted = parsed.log.changes.filter((c) => (c as Record<string, unknown>)["type"] === "defaulted-choose");

    expect(defaulted.length).toBeGreaterThan(0);
    expect(missing).toStrictEqual([]);
  });

  it("loads the same content keys", () => {
    expect(contentKeys(parsed.data!)).toStrictEqual(summary.plugins);
  });

  it("builds the same content lists", () => {
    const lists = (content: Record<string, string[]>) =>
      Object.fromEntries(Object.entries(content).map(([label, items]) => [label, sorted(items)]));

    expect(lists(template.content)).toStrictEqual(lists(summary.content));
  });

  it("builds the same top-level selections", () => {
    const entries = (s: BuiltTemplate["summary"]) =>
      s.map((entry) => ({ ...entry, optionKeys: sorted(entry.optionKeys) }));

    expect(entries(template.summary)).toStrictEqual(entries(summary.templateSummary));
  });
});
