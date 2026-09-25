import { readFileSync } from "node:fs";
import { describe, expect, it } from "vitest";
import {
  addClass,
  addInventoryItem,
  addLevel,
  addStartingEquipment,
  deselect,
  emptyCharacter,
  evaluate,
  exportCharacter,
  removeClass,
  removeInventoryItem,
  removeLevel,
  select,
  setClass,
  setField,
  setValue,
} from "@dmv/pubdoor";

const SELECTIONS = "~:orcpub.entity.strict/selections";
const OPTIONS = "~:orcpub.entity.strict/options";
const OPTION = "~:orcpub.entity.strict/option";
const KEY = "~:orcpub.entity.strict/key";
const DB_ID = "~:db/id";

type Node = Record<string, unknown>;

function read(url: URL): Node {
  return JSON.parse(readFileSync(url, "utf8")) as Node;
}

/** An entity from event_handlers_test.clj (scripts/event-handler-fixtures.clj). */
function handlerFixture(name: string): Node {
  return read(new URL(`fixtures/event-handlers/${name}.json`, import.meta.url));
}

function goldenCharacter(name: string): Node {
  return read(new URL(`../../fixtures/characters/${name}.strict.json`, import.meta.url));
}

function selectionsOf(node: Node): Node[] {
  return (node[SELECTIONS] as Node[] | undefined) ?? [];
}

function topSelection(entity: object, key: string): Node | undefined {
  return selectionsOf(entity as Node).find((s) => s[KEY] === `~:${key}`);
}

function classOption(entity: object, classKey: string): Node {
  const classes = topSelection(entity, "class")![OPTIONS] as Node[];
  return classes.find((c) => c[KEY] === `~:${classKey}`)!;
}

function levelsSelection(entity: object, classKey: string): Node {
  return selectionsOf(classOption(entity, classKey)).find((s) => s[KEY] === "~:levels")!;
}

function levelKeys(entity: object, classKey: string): unknown[] {
  return (levelsSelection(entity, classKey)[OPTIONS] as Node[]).map((l) => l[KEY]);
}

function setLevel(entity: object, classKey: string, level: number): object {
  let e = entity;
  while (levelKeys(e, classKey).length < level) e = addLevel(e, classKey);
  while (levelKeys(e, classKey).length > level) e = removeLevel(e, classKey);
  return e;
}

function levelRange(level: number): string[] {
  return Array.from({ length: level }, (_, i) => `~:level-${i + 1}`);
}

/**
 * The entity with every selections list keyed by selection key, at every
 * depth, so that sibling-selection order is ignored. Option arrays keep
 * their order.
 */
function ignoringSelectionOrder(x: unknown): unknown {
  if (Array.isArray(x)) return x.map(ignoringSelectionOrder);
  if (x !== null && typeof x === "object") {
    return Object.fromEntries(
      Object.entries(x).map(([k, v]) =>
        k === SELECTIONS
          ? [k, Object.fromEntries((v as Node[]).map((s) => [s[KEY], ignoringSelectionOrder(s)]))]
          : [k, ignoringSelectionOrder(v)],
      ),
    );
  }
  return x;
}

// Port of test/cljc/orcpub/dnd/e5/event_handlers_test.clj.
describe("event_handlers_test", () => {
  const character = handlerFixture("character");

  it.each([
    ["set-class-level--add-level", 5],
    ["set-class-level--add-multiple-levels", 20],
    ["set-class-level--remove-level", 3],
    ["set-class-level--level-1", 1],
    ["set-class-level--same-level", 4],
  ])("%s: level %i", (_, level) => {
    expect(levelKeys(setLevel(character, "barbarian", level), "barbarian")).toStrictEqual(
      levelRange(level),
    );
  });

  it("test-set-level--round-trip: keeps the ids of the levels selection", () => {
    const strict = handlerFixture("test-set-level--round-trip");
    const updated = setLevel(strict, "barbarian", 7);

    expect(levelKeys(updated, "barbarian")).toStrictEqual(levelRange(7));
    expect(levelsSelection(updated, "barbarian")[DB_ID]).toBe(
      levelsSelection(strict, "barbarian")[DB_ID],
    );

    const levels = levelsSelection(updated, "barbarian");
    levels[OPTIONS] = (levels[OPTIONS] as Node[]).slice(0, 4);
    expect(updated).toStrictEqual(strict);
  });

  it("test-set-class--round-trip: keeps the id of the class selection", () => {
    const strict = handlerFixture("test-set-class--round-trip");
    const updated = setClass(setClass(strict, 0, "bard"), 0, "barbarian");

    expect(topSelection(updated, "class")![DB_ID]).toBe(topSelection(strict, "class")![DB_ID]);
    expect(levelKeys(updated, "barbarian")).toStrictEqual(levelRange(1));
  });

  // The original's calls are commented out (they used non-SRD backgrounds),
  // so it checks only the strict round-trip.
  it("test-add-starting-equipment--round-trip", () => {
    const strict = handlerFixture("test-add-starting-equipment--round-trip");

    expect(exportCharacter(strict)).toStrictEqual(strict);
  });

  it("add-inventory-item--round-trip", () => {
    const strict = handlerFixture("add-inventory-item--round-trip");
    const updated = removeInventoryItem(addInventoryItem(strict, "weapons", "dagger"), "weapons", "dagger");

    expect(updated).toStrictEqual(strict);
  });

  it("update-single-select--round-trip", () => {
    const strict = handlerFixture("update-single-select--round-trip");
    const updated = select(select(strict, ["race"], "dwarf"), ["race"], "elf");

    expect(updated).toStrictEqual(strict);
  });

  // The original toggles options of [:skill-profs], but no selection refers
  // to that path since options.cljc:865 commented out the ref, so the
  // builder cannot reach it. The port runs the same toggles on the human's
  // language selection, with the original's selection id.
  it("update-multi-select--round-trip", () => {
    const original = handlerFixture("update-multi-select--round-trip");
    const strict = select(select(emptyCharacter(), ["race"], "human"), ["languages"], "giant") as Node;
    topSelection(strict, "languages")![DB_ID] = selectionsOf(original)[0][DB_ID];

    const withoutGiant = select(deselect(strict, ["languages"], "giant"), ["languages"], "orc");
    const changedBack = select(deselect(withoutGiant, ["languages"], "orc"), ["languages"], "giant");

    expect(changedBack).toStrictEqual(strict);
  });
});

describe("emptyCharacter", () => {
  it("is the builder's new character: a level 1 barbarian with its starting equipment", () => {
    const e = emptyCharacter();
    const { built } = evaluate(e);

    expect(selectionsOf(e as Node).map((s) => s[KEY])).toStrictEqual([
      "~:ability-scores",
      "~:class",
      "~:weapons",
      "~:equipment",
    ]);
    expect(built["levels"]).toMatchObject({ barbarian: { "class-level": 1 } });
    expect(Object.keys(built["weapons"] as object)).toStrictEqual(["javelin"]);
    expect(Object.keys(built["equipment"] as object)).toStrictEqual(["explorers-pack"]);
  });
});

describe("select and deselect", () => {
  const human = select(emptyCharacter(), ["race"], "human");

  it("throws where the builder refuses the option, with the reason", () => {
    expect(() => select(human, ["languages"], "common")).toThrow(/already have this language/);
  });

  it("throws for an option that is already selected", () => {
    expect(() => select(human, ["race"], "human")).toThrow(/already selected/);
  });

  it("throws for an unknown option or selection", () => {
    expect(() => select(human, ["race"], "no-such-race")).toThrow(/no option no-such-race/);
    expect(() => select(human, ["no-such-selection"], "x")).toThrow(/No available selection/);
  });

  it("points to the mutation the builder uses for classes", () => {
    expect(() => select(human, ["class"], "wizard")).toThrow(/setClass/);
  });

  it("cannot deselect a single-select option, as in the builder", () => {
    expect(() => deselect(human, ["race"], "human")).toThrow(/cannot deselect/);
  });

  it("deselects a multi-select option", () => {
    const withGiant = select(human, ["languages"], "giant");

    expect(JSON.stringify(topSelection(withGiant, "languages"))).toContain("~:giant");
    expect(JSON.stringify(deselect(withGiant, ["languages"], "giant"))).not.toContain("~:giant");
  });

  it("adds the background's starting equipment when a background is selected", () => {
    const acolyte = select(human, ["background"], "acolyte");

    expect(Object.keys(evaluate(acolyte).built["equipment"] as object)).toEqual(
      expect.arrayContaining(["clothes-common", "incense", "pouch", "vestements"]),
    );
    expect(addStartingEquipment(acolyte, "acolyte")).toStrictEqual(acolyte);
  });
});

describe("setValue and setField", () => {
  it("sets a character value, with keywords in Transit form", () => {
    const e = setValue(setValue(emptyCharacter(), "character-name", "Brannor"), "worn-armor", "~:chain-mail");

    expect(e["~:orcpub.entity.strict/values" as keyof object]).toMatchObject({
      "~:orcpub.dnd.e5.character/character-name": "Brannor",
      "~:orcpub.dnd.e5.character/worn-armor": "~:chain-mail",
    });
  });

  it("sets the ability scores", () => {
    const scores = {
      "~:orcpub.dnd.e5.character/str": 8,
      "~:orcpub.dnd.e5.character/dex": 10,
      "~:orcpub.dnd.e5.character/con": 12,
      "~:orcpub.dnd.e5.character/int": 13,
      "~:orcpub.dnd.e5.character/wis": 14,
      "~:orcpub.dnd.e5.character/cha": 15,
    };
    const e = setField(emptyCharacter(), ["ability-scores", "standard-scores"], scores);

    expect(evaluate(e).built["abilities"]).toMatchObject({ "orcpub.dnd.e5.character/str": 8 });
  });

  it("sets a level's hit points by manual entry", () => {
    const level2 = addLevel(emptyCharacter(), "barbarian");
    const path = ["class", "barbarian", "levels", "level-2", "hit-points", "manual-entry"];
    const maxHitPoints = (roll: number) =>
      evaluate(setField(level2, path, roll)).built["max-hit-points"] as number;

    expect(maxHitPoints(10) - maxHitPoints(1)).toBe(9);
  });
});

describe("classes and levels", () => {
  it("adds a class that meets its prerequisites", () => {
    // STR 15 meets the fighter's STR or DEX 13.
    const e = addClass(emptyCharacter(), "fighter");

    expect(evaluate(e).built["levels"]).toMatchObject({
      barbarian: { "class-level": 1 },
      fighter: { "class-level": 1 },
    });
  });

  it("refuses a class whose prerequisites fail", () => {
    // INT 12 does not meet the wizard's INT 13.
    expect(() => addClass(emptyCharacter(), "wizard")).toThrow(/requires/);
  });

  it("makes the next class first when the first is removed", () => {
    const e = removeClass(addClass(emptyCharacter(), "fighter"), "barbarian");

    // The fighter has no fixed starting items, so the barbarian's javelins go.
    expect(Object.keys(evaluate(e).built["levels"] as object)).toStrictEqual(["fighter"]);
    expect(JSON.stringify(e)).not.toContain("~:javelin");
  });

  it("keeps the level between 1 and 20", () => {
    expect(() => removeLevel(emptyCharacter(), "barbarian")).toThrow(/removeClass/);
    expect(() => addLevel(setLevel(emptyCharacter(), "barbarian", 20), "barbarian")).toThrow(/highest/);
  });
});

// The seed of M4's parity test: the builder's steps rebuild fighter-1.
// fighter-1 was built by the M0 generator in its own order, so sibling
// selections are compared without order (decided on ORC-19).
describe("fighter-1 from the builder's steps", () => {
  it("rebuilds fighter-1.strict.json", () => {
    const steps: ((e: object) => object)[] = [
      (e) => setClass(e, 0, "fighter"),
      (e) => select(e, ["alignment"], "lawful-good"),
      (e) => select(e, ["race"], "human"),
      (e) => select(e, ["race", "human", "subrace"], "damaran"),
      (e) => select(e, ["race", "human", "variant"], "standard-human"),
      (e) => select(e, ["background"], "acolyte"),
      (e) => select(e, ["languages"], "dwarvish"),
      (e) => select(e, ["languages"], "elvish"),
      (e) => select(e, ["languages"], "giant"),
      (e) => select(e, ["background", "acolyte", "starting-equipment-holy-symbol"], "amulet"),
      (e) => select(e, ["background", "acolyte", "starting-equipment-prayer-book-wheel"], "prayer-book"),
      (e) => select(e, ["class", "fighter", "fighting-style"], "defense"),
      (e) => select(e, ["class", "fighter", "starting-equipment-armor"], "chain-mail"),
      (e) => select(e, ["class", "fighter", "starting-equipment-weapons"], "martial-weapon-and-shield"),
      (e) =>
        select(
          e,
          ["class", "fighter", "starting-equipment-weapons", "martial-weapon-and-shield", "starting-equipment-martial-weapon"],
          "longsword",
        ),
      (e) => select(e, ["class", "fighter", "starting-equipment-additional-weapons"], "two-handaxes"),
      (e) => select(e, ["class", "fighter", "starting-equipment-equipment-pack"], "dungeoneers-pack"),
      (e) => select(e, ["class", "fighter", "skill-proficiency"], "athletics"),
      (e) => select(e, ["class", "fighter", "skill-proficiency"], "perception"),
      (e) => setValue(e, "character-name", "Brannor Ironfist"),
      (e) => setValue(e, "xps", 0),
      (e) => setValue(e, "worn-armor", "~:chain-mail"),
      (e) => setValue(e, "wielded-shield", "~:shield"),
      (e) => setValue(e, "main-hand-weapon", "~:longsword"),
      (e) => setValue(e, "off-hand-weapon", "~:shield"),
    ];
    const built = steps.reduce((e, step) => step(e), emptyCharacter());

    expect(ignoringSelectionOrder(built)).toStrictEqual(ignoringSelectionOrder(goldenCharacter("fighter-1")));
  });
});
