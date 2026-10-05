import { describe, expect, it } from "vitest";
import {
  buildTemplate,
  emptyCharacter,
  evaluate,
  orcbrewToEdn,
  parseOrcbrew,
  select,
  setClass,
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

/** CON in the built character for entity. */
function con(entity: object, homebrew: Record<string, object>): number {
  const built = evaluate(entity, { homebrew }).built as Record<string, Record<string, number>>;
  return built["abilities"]!["orcpub.dnd.e5.character/con"]!;
}

const qualified = (k: string) => `orcpub.dnd.e5.character/${k}`;

// Linear ORC-40, decided 2026-10-04: option 2, normalize on import.
describe("parseOrcbrew: bare ability keys (ORC-40)", () => {
  const { data, log } = parse("drift-10-ability-key-forms");
  const plugin = data!["drift-10-ability-key-forms"] as Plugin;

  it("rewrites :con-style keys in :abilities and :ability-increases, one change each", () => {
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
    const raceCon = (race: string) => con(select(emptyCharacter(), ["race"], race, { homebrew: data! }), data!);

    expect(raceCon("oxkin") - raceCon("hawkkin")).toBe(2);
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

// The other places homebrew holds ability keys that the engine reads as
// qualified keys, and the case where an item has both forms.
describe("parseOrcbrew: bare ability keys in classes, subclasses and feats (ORC-40)", () => {
  const { data, log } = parse("drift-12-ability-key-places");
  const plugin = data!["drift-12-ability-key-places"] as Plugin;
  const item = (type: string, key: string) => contentOf(plugin, type)[kw(key)]!;

  it("logs one change per bare key", () => {
    expect(
      changesOfType(log, "normalized-ability-key").map(({ path, from, to, dropped }) => ({ path, from, to, dropped })),
    ).toStrictEqual([
      {
        path: ["orcpub.dnd.e5/classes", "warden", "profs", "save"],
        from: "con",
        to: qualified("con"),
        dropped: undefined,
      },
      {
        path: ["orcpub.dnd.e5/classes", "warden", "spellcasting", "ability"],
        from: "wis",
        to: qualified("wis"),
        dropped: undefined,
      },
      {
        path: ["orcpub.dnd.e5/subclasses", "ember-scholar", "level-modifiers", 0, "value", "ability"],
        from: "int",
        to: qualified("int"),
        dropped: undefined,
      },
      { path: ["orcpub.dnd.e5/races", "stonekin", "abilities"], from: "con", to: qualified("con"), dropped: true },
      {
        path: ["orcpub.dnd.e5/feats", "mighty-grip", "ability-increases"],
        from: "dex",
        to: qualified("dex"),
        dropped: true,
      },
      {
        path: ["orcpub.dnd.e5/feats", "mighty-grip", "ability-increases"],
        from: "str",
        to: qualified("str"),
        dropped: undefined,
      },
      {
        path: ["orcpub.dnd.e5/feats", "mighty-grip", "prereqs"],
        from: "str",
        to: qualified("str"),
        dropped: undefined,
      },
    ]);
  });

  it("rewrites a class's saving throws and spellcasting ability", () => {
    const warden = item("classes", "warden");

    expect((warden[kw("profs")] as Record<string, unknown>)[kw("save")]).toStrictEqual({
      [kw(qualified("con"))]: true,
      [kw(qualified("wis"))]: true,
    });
    expect((warden[kw("spellcasting")] as Record<string, unknown>)[kw("ability")]).toBe(kw(qualified("wis")));
  });

  it("gives the class its saving throws", () => {
    const entity = setClass(emptyCharacter(), 0, "warden", { homebrew: data! });
    const built = evaluate(entity, { homebrew: data! }).built as Record<string, string[]>;

    expect(built["saving-throws"]).toEqual(expect.arrayContaining([qualified("con"), qualified("wis")]));
  });

  it("rewrites the ability of a :spell level-modifier", () => {
    const modifiers = item("subclasses", "ember-scholar")[kw("level-modifiers")] as Record<
      string,
      Record<string, unknown>
    >[];

    expect(modifiers.map((m) => m[kw("value")]![kw("ability")])).toStrictEqual([
      kw(qualified("int")),
      kw(qualified("int")),
    ]);
  });

  it("rewrites a feat's prerequisites and increases", () => {
    const feat = item("feats", "mighty-grip");

    expect(feat[kw("prereqs")]).toStrictEqual({ "~#set": [kw(qualified("str"))] });
    expect(new Set((feat[kw("ability-increases")] as { "~#set": string[] })["~#set"])).toStrictEqual(
      new Set([kw(qualified("str")), kw(qualified("dex"))]),
    );
  });

  it("keeps the qualified value when an item has both forms, and logs both values", () => {
    const [change] = changesOfType(log, "normalized-ability-key").filter(
      (c) => (c["path"] as string[])[1] === "stonekin",
    );

    expect(item("races", "stonekin")[kw("abilities")]).toStrictEqual({ [kw(qualified("con"))]: 1 });
    expect(change).toMatchObject({ "dropped-value": 2, "kept-value": 1 });
    expect(change!["description"]).toMatch(/^Dropped :con 2 .* already has :orcpub\.dnd\.e5\.character\/con 1$/);
    expect(con(select(emptyCharacter(), ["race"], "stonekin", { homebrew: data! }), data!)).toBe(
      con(emptyCharacter(), data!) + 1,
    );
  });

  it("finds nothing to rewrite in its own export", () => {
    expect(parseOrcbrew(orcbrewToEdn(data!)).log.changes).toStrictEqual([]);
  });
});

// The builder shows 1 when :choose is unset (views.cljs
// option-proficiency-choice) and writes :choose only when the author changes
// it. Without it, the engine builds the skill selection with no maximum.
describe("parseOrcbrew: skill choices without :choose (ORC-39)", () => {
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

  it("sets :choose to 1 on :multiclass-skill-options too", () => {
    const pack = parse("drift-12-ability-key-places");

    expect(
      changesOfType(pack.log, "defaulted-choose").map(({ path, field, to }) => ({ path, field, to })),
    ).toStrictEqual([
      { path: ["orcpub.dnd.e5/classes", "warden", "profs", "multiclass-skill-options"], field: "choose", to: 1 },
    ]);
    expect(
      selectionAt(buildTemplate(pack.data!).shape, ["class", "warden", "multiclass-skill-proficiency"]),
    ).toMatchObject({
      min: 1,
      max: 1,
    });
  });
});
