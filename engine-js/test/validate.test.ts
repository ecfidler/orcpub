// The homebrew validators (ORC-41): the old builders' save checks, one for
// each type. Every item in every fixture pack must pass, except the three
// whose names the importer replaced with placeholders, and a record without
// a required field must report that field.
import { existsSync, readFileSync } from "node:fs";
import { describe, expect, it } from "vitest";
import { parseOrcbrew, validate, type Validation, type ValidationProblem } from "@pubdoor/dmv";
import { fixtureNames, fixtures, kw, readFixtureText, unkw } from "./support.js";

/** The validator for each pack content type. */
const validators: Record<string, (record: object) => Validation> = {
  "orcpub.dnd.e5/races": validate.race,
  "orcpub.dnd.e5/subraces": validate.subrace,
  "orcpub.dnd.e5/classes": validate.class,
  "orcpub.dnd.e5/subclasses": validate.subclass,
  "orcpub.dnd.e5/backgrounds": validate.background,
  "orcpub.dnd.e5/feats": validate.feat,
  "orcpub.dnd.e5/spells": validate.spell,
  "orcpub.dnd.e5/languages": validate.language,
  "orcpub.dnd.e5/invocations": validate.invocation,
  "orcpub.dnd.e5/boons": validate.boon,
  "orcpub.dnd.e5/selections": validate.selection,
  "orcpub.dnd.e5/monsters": validate.monster,
  "orcpub.dnd.e5/encounters": validate.encounter,
};

type Items = Record<string, Record<string, object>>;

/** The problems without pred, which is the failed predicate as text. */
const reported = (v: Validation) => v.problems.map(({ path, reason }) => ({ path, reason }));

/** Each item in the packs that fail validation: "<type> <key>" → problems without pred. */
function failures(data: Record<string, Items>): Record<string, Omit<ValidationProblem, "pred">[]> {
  const failed: Record<string, Omit<ValidationProblem, "pred">[]> = {};
  for (const plugin of Object.values(data)) {
    for (const [type, items] of Object.entries(plugin)) {
      const check = validators[unkw(type)];
      if (!check || typeof items !== "object") continue;
      for (const [key, item] of Object.entries(items)) {
        const validation = check(item);
        if (!validation.ok) failed[`${unkw(type).replace(/^orcpub\.dnd\.e5\//, "")} ${unkw(key)}`] = reported(validation);
      }
    }
  }
  return failed;
}

describe("validate", () => {
  it("has a validator for each type the old builders save", () => {
    expect(Object.keys(validate).sort()).toEqual([
      "background",
      "boon",
      "class",
      "encounter",
      "feat",
      "invocation",
      "language",
      "magicItem",
      "monster",
      "race",
      "selection",
      "spell",
      "subclass",
      "subrace",
    ]);
  });

  it("passes every item in the fixture packs, except the importer's placeholders", () => {
    const failed = {};
    for (const pack of fixtureNames("orcbrew", ".orcbrew")) {
      const parsed = parseOrcbrew(readFixtureText(`orcbrew/${pack}.orcbrew`), { name: pack });
      Object.assign(failed, failures(parsed.data as Record<string, Items>));
    }

    // The importer names a nameless item "[Missing ... Name]" and a
    // nameless option "[Option n]". The old save rejected both, because a
    // name must start with a letter. hollow-gift also has two options with
    // the same name.
    expect(failed).toEqual({
      "classes unnamed-class": [{ path: ["name"], reason: "invalid" }],
      "spells unfinished-bolt": [{ path: ["name"], reason: "invalid" }],
      "selections hollow-gift": [
        { path: ["options", 0, "name"], reason: "invalid" },
        { path: ["options", 1, "name"], reason: "duplicate" },
        { path: ["options", 2, "name"], reason: "duplicate" },
      ],
    });
  });

  const exportFile = new URL("orcbrew/private/all-content3.orcbrew", fixtures);
  it.skipIf(!existsSync(exportFile))("passes every item in the private export", () => {
    const parsed = parseOrcbrew(readFileSync(exportFile, "utf8"), { name: "all-content3" });

    expect(failures(parsed.data as Record<string, Items>)).toEqual({});
  });

  it("reports each missing required field by its path", () => {
    const pack = { [kw("option-pack")]: "Test" };

    expect(reported(validate.race({ ...pack }))).toEqual([
      { path: ["name"], reason: "missing" },
      { path: ["key"], reason: "missing" },
    ]);
    expect(reported(validate.background({ [kw("name")]: "Sailor" }))).toEqual([
      { path: ["option-pack"], reason: "missing" },
    ]);
    expect(reported(validate.subrace({ ...pack, [kw("name")]: "Deep Gnome" }))).toEqual([
      { path: ["race"], reason: "missing" },
    ]);
    expect(reported(validate.subclass({ ...pack, [kw("name")]: "Oath of Ash" }))).toEqual([
      { path: ["class"], reason: "missing" },
    ]);
    expect(reported(validate.spell({ ...pack, [kw("name")]: "Zap", [kw("level")]: 1 }))).toEqual([
      { path: ["school"], reason: "missing" },
      { path: ["spell-lists"], reason: "missing" },
    ]);
    expect(
      reported(validate.monster({ ...pack, [kw("name")]: "Ogre", [kw("hit-points")]: { [kw("die")]: 10 } })),
    ).toEqual([{ path: ["hit-points", "die-count"], reason: "missing" }]);
    expect(reported(validate.magicItem({ [kw("orcpub.dnd.e5.magic-items/type")]: kw("weapon") }))).toEqual([
      { path: ["orcpub.dnd.e5.magic-items/name"], reason: "missing" },
    ]);
  });

  it("reports a blank name as missing, and every part of the spell spec", () => {
    expect(reported(validate.spell({ [kw("name")]: "", [kw("level")]: 1 }))).toEqual([
      { path: ["key"], reason: "missing" },
      { path: ["school"], reason: "missing" },
      { path: ["name"], reason: "missing" },
      { path: ["option-pack"], reason: "missing" },
      { path: ["spell-lists"], reason: "missing" },
    ]);
  });

  it("reports a field with the wrong value as invalid", () => {
    const spell = {
      [kw("option-pack")]: "Test",
      [kw("name")]: "Zap",
      [kw("school")]: "evocation",
      [kw("level")]: 12,
      [kw("spell-lists")]: { [kw("wizard")]: true },
    };

    expect(reported(validate.spell(spell))).toEqual([{ path: ["level"], reason: "invalid" }]);
  });

  it("reports options with the same name as the old selection save did", () => {
    const selection = {
      [kw("option-pack")]: "Test",
      [kw("name")]: "Gift",
      [kw("options")]: [{ [kw("name")]: "Sea Gift" }, { [kw("name")]: "sea  gift" }, { [kw("name")]: "Sky Gift" }],
    };

    expect(reported(validate.selection(selection))).toEqual([
      { path: ["options", 0, "name"], reason: "duplicate" },
      { path: ["options", 1, "name"], reason: "duplicate" },
    ]);
  });

  it("returns the item with its key from the name, as the old save stores it", () => {
    const { ok, item } = validate.feat({ [kw("option-pack")]: "Test", [kw("name")]: "Iron Will" });

    expect(ok).toBe(true);
    expect(item).toEqual({ [kw("option-pack")]: "Test", [kw("name")]: "Iron Will", [kw("key")]: kw("iron-will") });
  });

  it("keeps a key the item already has", () => {
    const { item } = validate.feat({ [kw("option-pack")]: "Test", [kw("name")]: "Iron Will", [kw("key")]: kw("will") });

    expect(item).toMatchObject({ [kw("key")]: kw("will") });
  });

  it("accepts the Transit-JSON text as well as the parsed value", () => {
    const race = { [kw("option-pack")]: "Test", [kw("name")]: "Elfkin" };

    expect(validate.race(JSON.stringify(race))).toEqual(validate.race(race));
  });

  it("throws unless the record is a map", () => {
    expect(() => validate.race([])).toThrow(/race record is not a map/);
  });
});
