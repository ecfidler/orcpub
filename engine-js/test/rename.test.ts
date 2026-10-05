// renameKey, patch D4 (orc-alchemy docs/plan/01-compatibility-contract.md,
// Known quirks): a rename rewrites every reference that
// import_validation.cljs key-reference-map lists, so content renamed to
// resolve a key conflict keeps working.
import { describe, expect, it } from "vitest";
import { parseOrcbrew, reconcileMissingContent, renameKey, type KeyRename } from "@pubdoor/dmv";
import { builtInKeys, contentOf as packContent, danglingReferences, kw, type ContentMap, type Plugin } from "./support.js";

// A spellcasting class with a subclass, a plugin selection used through
// level-selections, and two spells on the class's spell list.
const TINKER = `{:orcpub.dnd.e5/classes
 {:tinker {:key :tinker :name "Tinker" :option-pack "Rename Test" :hit-die 8
           :ability-increase-levels [4 8 12 16 19]
           :subclass-level 1 :subclass-title "Tinker Guild"
           :spellcasting {:level-factor 1 :ability :orcpub.dnd.e5.character/int
                          :cantrips-known {1 1} :spells-known {1 1}}
           :level-selections [{:type :gadgets :level 1 :num 1}]}}
 :orcpub.dnd.e5/subclasses
 {:gearwright {:key :gearwright :name "Gearwright" :class :tinker :option-pack "Rename Test"}}
 :orcpub.dnd.e5/selections
 {:gadgets {:key :gadgets :name "Gadgets" :option-pack "Rename Test"
            :options [{:name "Spring Boots" :description "Jump farther."}
                      {:name "Grapnel" :description "Climb faster."}]}}
 :orcpub.dnd.e5/spells
 {:spark {:key :spark :name "Spark" :option-pack "Rename Test" :level 0 :school "evocation"
          :spell-lists {:tinker true :wizard false}}
  :cog-shield {:key :cog-shield :name "Cog Shield" :option-pack "Rename Test" :level 1 :school "abjuration"
               :spell-lists {:tinker true}}}}`;

const PACK = "tinker";

function tinker() {
  const parsed = parseOrcbrew(TINKER, { name: PACK });
  expect(parsed.success).toBe(true);
  expect(parsed.skipped).toStrictEqual([]);
  return parsed.data!;
}

// Strict entities as Transit-JSON (verbose).
const KEY = kw("orcpub.entity.strict/key");
const opt = (key: string, selections?: object[]) =>
  selections ? { [KEY]: kw(key), [kw("orcpub.entity.strict/selections")]: selections } : { [KEY]: kw(key) };
const one = (key: string, option: object) => ({ [KEY]: kw(key), [kw("orcpub.entity.strict/option")]: option });
const many = (key: string, options: object[]) => ({ [KEY]: kw(key), [kw("orcpub.entity.strict/options")]: options });

/**
 * A level 1 Tinker of the Gearwright guild with a gadget, a cantrip and a
 * spell. The spell selections are named after the class's display name,
 * "Tinker (tinker)", so they keep their keys when the class is renamed.
 */
function tinkerCharacter(classKey: string, gadgetSelection = "gadgets"): object {
  return {
    [kw("orcpub.entity.strict/selections")]: [
      many("class", [
        opt(classKey, [
          many("levels", [opt("level-1", [one(gadgetSelection, opt("spring-boots")), one("tinker-guild", opt("gearwright"))])]),
          many("tinker-tinker-cantrips-known", [opt("spark")]),
          many("tinker-tinker-spells-known", [opt("cog-shield")]),
        ]),
      ]),
    ],
  };
}

/** The items of one content type in the tinker pack. */
const contentOf = (homebrew: Record<string, object>, type: string): ContentMap =>
  packContent(homebrew[PACK] as Plugin, type);

describe("renameKey", () => {
  it("builds the character with every key resolved before a rename", () => {
    expect(reconcileMissingContent(tinkerCharacter("tinker"), tinker()).hasMissing).toBe(false);
  });

  it("rewrites a renamed class's subclass and spell-list references", () => {
    const renamed = renameKey(tinker(), { pack: PACK, contentType: "orcpub.dnd.e5/classes", from: "tinker", to: "tinker-rt" });

    expect(Object.keys(contentOf(renamed, "classes"))).toStrictEqual(["~:tinker-rt"]);
    expect(contentOf(renamed, "classes")["~:tinker-rt"]!["~:key"]).toBe("~:tinker-rt");
    expect(contentOf(renamed, "subclasses")["~:gearwright"]!["~:class"]).toBe("~:tinker-rt");
    expect(contentOf(renamed, "spells")["~:spark"]!["~:spell-lists"]).toStrictEqual({ "~:wizard": false, "~:tinker-rt": true });
    expect(contentOf(renamed, "spells")["~:cog-shield"]!["~:spell-lists"]).toStrictEqual({ "~:tinker-rt": true });
  });

  // The ORC-35 done-when: the C3 identity check on a character that uses
  // the renamed class. Before patch D4 the spells kept the old class key, so
  // the cantrip and the spell had no options to resolve to.
  it("leaves no dangling reference for a character using the renamed class", () => {
    const renamed = renameKey(tinker(), { pack: PACK, contentType: "orcpub.dnd.e5/classes", from: "tinker", to: "tinker-rt" });

    // The pack's own references all resolve (the C3 pack check)...
    expect(danglingReferences(renamed, builtInKeys())).toStrictEqual([]);
    // ...and so do the character's keys.
    expect(reconcileMissingContent(tinkerCharacter("tinker-rt"), renamed)).toStrictEqual({
      hasMissing: false,
      items: [],
      unresolvedOptions: [],
    });
    // The old rename moved only the class and its subclasses' :class, and
    // the same check finds the spells it left behind.
    const oldStyle = tinker();
    const classes = contentOf(oldStyle, "classes");
    classes["~:tinker-rt"] = { ...classes["~:tinker"]!, "~:key": "~:tinker-rt" };
    delete classes["~:tinker"];
    contentOf(oldStyle, "subclasses")["~:gearwright"]!["~:class"] = "~:tinker-rt";
    expect(danglingReferences(oldStyle, builtInKeys()).map((ref) => ref.from).sort()).toStrictEqual([
      "spell cog-shield",
      "spell spark",
    ]);

    // The character saved with the old key now needs a remap.
    expect(reconcileMissingContent(tinkerCharacter("tinker"), renamed).items.map((item) => item.key)).toStrictEqual([
      "tinker",
    ]);
  });

  it("rewrites the level-selections of a renamed selection", () => {
    const renamed = renameKey(tinker(), { pack: PACK, contentType: "orcpub.dnd.e5/selections", from: "gadgets", to: "gizmos" });

    expect(contentOf(renamed, "classes")["~:tinker"]!["~:level-selections"]).toStrictEqual([
      { "~:type": "~:gizmos", "~:level": 1, "~:num": 1 },
    ]);
    expect(danglingReferences(renamed, builtInKeys())).toStrictEqual([]);
    expect(reconcileMissingContent(tinkerCharacter("tinker", "gizmos"), renamed).hasMissing).toBe(false);
  });

  it("changes nothing in other packs", () => {
    const other = parseOrcbrew(
      `{:orcpub.dnd.e5/spells {:spark {:key :spark :name "Spark" :option-pack "Other" :level 0 :spell-lists {:tinker true}}}}`,
      { name: "other", existing: tinker() },
    ).data!;
    const renamed = renameKey(other, { pack: PACK, contentType: "orcpub.dnd.e5/classes", from: "tinker", to: "tinker-rt" });

    expect(renamed["other"]).toStrictEqual(other["other"]);
  });

  it("accepts homebrew as text", () => {
    const rename: KeyRename = { pack: PACK, contentType: "orcpub.dnd.e5/classes", from: "tinker", to: "tinker-rt" };

    expect(renameKey(JSON.stringify(tinker()), rename)).toStrictEqual(renameKey(tinker(), rename));
  });

  it("throws for a missing pack or key, or a key already taken", () => {
    const homebrew = tinker();
    const classes = { pack: PACK, contentType: "orcpub.dnd.e5/classes" } as const;

    expect(() => renameKey(homebrew, { ...classes, pack: "nope", from: "tinker", to: "x" })).toThrow(/No pack named "nope"/);
    expect(() => renameKey(homebrew, { ...classes, from: "wizard", to: "x" })).toThrow('Pack "tinker" has no key "wizard" in orcpub.dnd.e5/classes');
    expect(() =>
      renameKey(homebrew, { pack: PACK, contentType: "orcpub.dnd.e5/spells", from: "spark", to: "cog-shield" }),
    ).toThrow('Pack "tinker" already has the key "cog-shield" in orcpub.dnd.e5/spells');
    expect(() => renameKey(homebrew, { ...classes, from: "tinker", to: "" })).toThrow(/to must be a non-empty string/);
  });
});
