import { readdirSync, readFileSync } from "node:fs";
import { isDeepStrictEqual } from "node:util";
import { gunzipSync } from "node:zlib";
import { describe, expect, it } from "vitest";
import { buildTemplate, parseOrcbrew, type TemplateSelection } from "@pubdoor/dmv";

// Each fixtures/orcbrew/<pack>.template.json records the old template chain's
// output for that pack loaded alone, and _srd-baseline.template.json.gz the
// SRD-only template. See fixtures/README.md.
const orcbrew = new URL("../../fixtures/orcbrew/", import.meta.url);

type Fixture = {
  content: Record<string, unknown[]>;
  templateSummary: unknown[];
  templateDelta: Delta;
};

type ShapeNode = Record<string, unknown> & { key: string };
type Delta = Record<string, unknown>;

const packs = readdirSync(orcbrew)
  .filter((file) => file.endsWith(".orcbrew"))
  .map((file) => file.slice(0, -".orcbrew".length))
  .sort();

function fixture(pack: string): Fixture {
  return JSON.parse(readFileSync(new URL(`${pack}.template.json`, orcbrew), "utf8")) as Fixture;
}

// A port of orcpub.oracle/shape-delta (scripts/orcpub/oracle.clj). Nodes are
// matched by key: selection nodes have "options" children and option nodes
// have "selections" children. The result keeps only what differs.

function diffChildren(
  baseline: ShapeNode[] | undefined,
  children: ShapeNode[] | undefined,
  childKey: string,
): Delta {
  const grandchildKey = childKey === "options" ? "selections" : "options";
  const byKey = new Map((baseline ?? []).map((b) => [b.key, b]));
  const present = new Set((children ?? []).map((c) => c.key));
  const removed = (baseline ?? []).map((b) => b.key).filter((k) => !present.has(k));
  const diffs = (children ?? []).flatMap((child) => {
    const b = byKey.get(child.key);
    if (!b) return [child];
    const diff = diffNode(b, child, grandchildKey);
    return diff ? [diff] : [];
  });
  return {
    ...(diffs.length ? { [childKey]: diffs } : {}),
    ...(removed.length ? { [`removed-${childKey}`]: removed } : {}),
  };
}

function diffNode(baseline: ShapeNode, node: ShapeNode, childKey: string): Delta | null {
  if (isDeepStrictEqual(baseline, node)) return null;
  const { [childKey]: children, ...scalars } = node;
  const { [childKey]: baselineChildren, ...baselineScalars } = baseline;
  // Clojure's get returns nil for a missing key, so a missing field equals null.
  const changed = Object.fromEntries(
    Object.entries(scalars).filter(([k, v]) => !isDeepStrictEqual(v, baselineScalars[k] ?? null)),
  );
  const removedFields = Object.keys(baselineScalars).filter((k) => !(k in scalars));
  return {
    ...diffChildren(baselineChildren as ShapeNode[], children as ShapeNode[], childKey),
    ...changed,
    key: node.key,
    ...(removedFields.length ? { "removed-fields": removedFields } : {}),
  };
}

function shapeDelta(baseline: object[], shape: object[]): Delta {
  return diffChildren(baseline as ShapeNode[], shape as ShapeNode[], "selections");
}

// The fixtures come from the JVM, whose hash maps and sets iterate in a
// different order than the browser's (fixtures/README.md finding 15). These
// selections build their options by iterating one, so only their option
// sets are compared. The browser's level-18 Magical Secrets also offers the
// 9th-level spells, which the JVM's misses; they are dropped here and
// checked on their own below.
function hashOrdered(key: string): boolean {
  return key === "asi" || key === "bard-magical-secrets" || key.endsWith("-spells-known");
}

function jvmView<T>(x: T): T {
  if (Array.isArray(x)) return x.map(jvmView) as T;
  if (x === null || typeof x !== "object") return x;
  const node: Record<string, unknown> = Object.fromEntries(
    Object.entries(x).map(([k, v]) => [k, jvmView(v)]),
  );
  if (typeof node["key"] === "string" && hashOrdered(node["key"])) {
    const options = node["options"] as ShapeNode[] | undefined;
    const removed = node["removed-options"] as string[] | undefined;
    if (options) {
      node["options"] = options
        .filter((o) => !(node["key"] === "bard-magical-secrets" && String(o["name"]).startsWith("9 - ")))
        .sort((a, b) => (a.key < b.key ? -1 : a.key > b.key ? 1 : 0));
    }
    if (removed) node["removed-options"] = [...removed].sort();
  }
  return node as T;
}

/** The selection at path, alternating selection and option keys. */
function selectionAt(shape: TemplateSelection[], path: string[]): TemplateSelection {
  let selections = shape;
  let selection: TemplateSelection | undefined;
  for (let i = 0; i < path.length; i += 2) {
    selection = selections.find((s) => s.key === path[i]);
    const option = selection?.options.find((o) => o.key === path[i + 1]);
    selections = option?.selections ?? [];
  }
  return selection!;
}

describe("buildTemplate", () => {
  const srd = buildTemplate();

  it("builds the SRD baseline", () => {
    const baseline = JSON.parse(
      gunzipSync(readFileSync(new URL("_srd-baseline.template.json.gz", orcbrew))).toString("utf8"),
    ) as { summary: unknown; shape: unknown };

    expect(srd.summary).toStrictEqual(baseline.summary);
    expect(jvmView(srd.shape)).toStrictEqual(jvmView(baseline.shape));
  });

  it("offers 9th-level spells to level-18 Magical Secrets", () => {
    const secrets = selectionAt(srd.shape, ["class", "bard", "levels", "level-18", "bard-magical-secrets"]);

    expect(secrets.options.filter((o) => o.name.startsWith("9 - "))).toHaveLength(15);
  });

  it("builds the SRD template from empty homebrew", () => {
    expect(buildTemplate({})).toStrictEqual(srd);
  });

  for (const pack of packs) {
    it(`${pack} builds the old template`, () => {
      const expected = fixture(pack);
      const { data } = parseOrcbrew(readFileSync(new URL(`${pack}.orcbrew`, orcbrew), "utf8"), {
        name: pack,
      });
      const built = buildTemplate(data!);

      expect(built.content).toStrictEqual(expected.content);
      expect(built.summary).toStrictEqual(expected.templateSummary);
      expect(shapeDelta(jvmView(srd.shape), jvmView(built.shape))).toStrictEqual(
        jvmView(expected.templateDelta),
      );
    });
  }
});
