import { describe, expect, it } from "vitest";
import {
  buildTemplate,
  emptyCharacter,
  evaluate,
  orcbrewToEdn,
  parseOrcbrew,
  select,
  type TemplateSelection,
} from "@pubdoor/dmv";
import { contentOf, kw, readFixture, readFixtureText, type Plugin } from "./support.js";

// parseOrcbrew rewrites two forms that the old engine accepted but ignored,
// and logs each rewrite in log.changes. See fixtures/README.md, finding 4
// and finding 9.

function parse(pack: string) {
  return parseOrcbrew(readFixtureText(`orcbrew/${pack}.orcbrew`), { name: pack });
}

function changesOfType(log: { changes: object[] }, type: string): Record<string, unknown>[] {
  return (log.changes as Record<string, unknown>[]).filter((change) => change["type"] === type);
}

/** The option with key under the top-level selection selectionKey. */
function topLevelOption(shape: TemplateSelection[], selectionKey: string, key: string) {
  return shape.find((s) => s.key === selectionKey)!.options.find((o) => o.key === key)!;
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

// Linear ORC-40, decided 2026-10-04: option 2, normalize on import.
describe("parseOrcbrew: bare ability keys (ORC-40)", () => {
  const { data, log } = parse("drift-10-ability-key-forms");
  const plugin = data!["drift-10-ability-key-forms"] as Plugin;

  it("rewrites :con-style keys in :abilities and :ability-increases, one change each", () => {
    const qualified = (k: string) => `orcpub.dnd.e5.character/${k}`;

    expect(
      changesOfType(log, "normalized-ability-key").map(({ path, from, to }) => ({ path, from, to })),
    ).toStrictEqual([
      { path: ["orcpub.dnd.e5/races", "oxkin", "abilities"], from: "con", to: qualified("con") },
      { path: ["orcpub.dnd.e5/races", "oxkin", "abilities"], from: "str", to: qualified("str") },
      { path: ["orcpub.dnd.e5/feats", "ox-heart", "ability-increases"], from: "con", to: qualified("con") },
    ]);
    expect(contentOf(plugin, "races")[kw("oxkin")]![kw("abilities")]).toStrictEqual({
      [kw(qualified("con"))]: 2,
      [kw(qualified("str"))]: 1,
    });
    expect(contentOf(plugin, "feats")[kw("ox-heart")]![kw("ability-increases")]).toStrictEqual({
      "~#set": [kw(qualified("con"))],
    });
  });

  it("leaves qualified keys alone", () => {
    expect(contentOf(plugin, "races")[kw("hawkkin")]![kw("abilities")]).toStrictEqual({
      [kw("orcpub.dnd.e5.character/dex")]: 2,
      [kw("orcpub.dnd.e5.character/wis")]: 1,
    });
  });

  it("makes a bare-key race add its increase", () => {
    const con = (race: string) => {
      const entity = select(emptyCharacter(), ["race"], race, { homebrew: data! });
      const built = evaluate(entity, { homebrew: data! }).built as Record<string, Record<string, number>>;
      return built["abilities"]!["orcpub.dnd.e5.character/con"]!;
    };

    expect(con("oxkin") - con("hawkkin")).toBe(2);
  });

  it("makes a bare-key feat add its increase", () => {
    const { shape } = buildTemplate(data!);

    expect(topLevelOption(shape, "feats", "ox-heart").modifiers).toContainEqual({
      key: "ability-increases",
      name: "CON",
      value: "+1",
    });
  });

  it("gives ironwrought-artificer-3 its +2 CON", () => {
    const { data: homebrew } = parse("duplicate-external-b");
    const { built } = evaluate(readFixture("characters/ironwrought-artificer-3.strict.json") as object, {
      homebrew: homebrew!,
    });

    expect((built["abilities"] as Record<string, number>)["orcpub.dnd.e5.character/con"]).toBe(17);
    expect(built["race-ability-increases"]).toStrictEqual({ "orcpub.dnd.e5.character/con": 2 });
  });

  it("finds nothing to rewrite in its own export", () => {
    expect(parseOrcbrew(orcbrewToEdn(data!)).log.changes).toStrictEqual([]);
  });
});

// The builder shows 1 when :choose is unset (views.cljs
// option-proficiency-choice) and writes :choose only when the author changes
// it. Without it, the engine builds the skill selection with no maximum.
describe("parseOrcbrew: :skill-options without :choose (ORC-39)", () => {
  const { data, log } = parse("drift-11-skill-options-without-choose");
  const plugin = data!["drift-11-skill-options-without-choose"] as Plugin;
  const profs = (key: string) =>
    (contentOf(plugin, "subclasses")[kw(key)]![kw("profs")] as Record<string, Record<string, unknown>>)[
      kw("skill-options")
    ]!;

  it("sets :choose to 1 and logs the change", () => {
    expect(changesOfType(log, "defaulted-choose").map(({ path, field, to }) => ({ path, field, to }))).toStrictEqual([
      { path: ["orcpub.dnd.e5/subclasses", "lorekeeper", "profs", "skill-options"], field: "choose", to: 1 },
    ]);
    expect(profs("lorekeeper")[kw("choose")]).toBe(1);
  });

  it("keeps a :choose the author set", () => {
    expect(profs("pathfinder")[kw("choose")]).toBe(2);
  });

  it("builds a skill selection of exactly one", () => {
    const { shape } = buildTemplate(data!);
    const skills = (subclass: string) =>
      selectionAt(shape, ["class", "rogue", "levels", "level-3", "roguish-archetype", subclass, "skill-proficiency"]);

    expect(skills("lorekeeper")).toMatchObject({ min: 1, max: 1 });
    expect(skills("pathfinder")).toMatchObject({ min: 2, max: 2 });
  });

  it("finds nothing to change in its own export", () => {
    expect(parseOrcbrew(orcbrewToEdn(data!)).log.changes).toStrictEqual([]);
  });
});
