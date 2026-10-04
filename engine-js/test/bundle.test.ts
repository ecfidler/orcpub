import { readdirSync, readFileSync, statSync } from "node:fs";
import { gzipSync } from "node:zlib";
import { describe, expect, it } from "vitest";

// What dist/pubdoor.js, the engine chunk, must leave out (orc-alchemy
// docs/plan/02-engine-library.md §Where the engine is built, wrinkle 8).
// Each string appears in one source file only, so its absence from the
// bundle shows that the namespace was not compiled in. :advanced keeps
// string literals as they are, as the control strings show.
const dist = new URL("../dist/", import.meta.url);
const bundle = readFileSync(new URL("pubdoor.js", dist), "utf8");

/**
 * The budget for dist/pubdoor.js: about 8% over the 1,577,000 bytes measured
 * in ORC-42. It was 2,025,012 bytes before ORC-42, with the monsters in it.
 */
const BUDGET_BYTES = 1_700_000;

const excluded: Record<string, string[]> = {
  // Non-SRD name tables (contract quirk list, doc 01).
  "character/random.cljc": ["The Bearded Clam"],
  "pdf_spec.cljc": ["Number of Attacks: "],
  "char_decision_tree.cljc": ["Is your character a hero or a villain?"],
  // Served as dist/content/monsters.json instead (orcpub.facade.no-monsters).
  "monsters.cljc": ["darkvision 120 ft., passive Perception 20", "Deep Speech, telepathy 120 ft."],
  // One string per file under templates/, all unreferenced and non-SRD.
  "templates/scag.cljc": ["Reckless Abandon"],
  "templates/ua_artificer.cljc": ["MAGIC ITEM NOT FOUND"],
  "templates/ua_bard.cljc": ["Mantle of Inspiration"],
  "templates/ua_base.cljc": ["Drunkard's Luck"],
  "templates/ua_cleric.cljc": ["Blessings of the Forge"],
  "templates/ua_feats.cljc": ["Warhammer Master"],
  "templates/ua_fighter.cljc": ["Implacable Mark"],
  "templates/ua_gothic_heroes.cljc": ["Unearthed Arcana: Gothic Heroes"],
  "templates/ua_mystic.cljc": ["Adaptive Body: Psychic Focus"],
  "templates/ua_options.cljc": ["Arcane Shot: Banishing Arrow"],
  "templates/ua_race_feats.cljc": ["Grudge Bearer Feat"],
  "templates/ua_revised_class_options.cljc": ["Speech of the Woods"],
  "templates/ua_revised_ranger.cljc": ["Greater Favored Enemy"],
  "templates/ua_skill_feats.cljc": ["carrying capacity of one size larger creature"],
  "templates/ua_sorcerer.cljc": ["Empowered Healing"],
  "templates/ua_warlock_and_wizard.cljc": ["Hexblade's Curse"],
};

// Strings from namespaces the engine does compile.
const included: Record<string, string> = {
  "spells.cljc": "You hurl a bubble of acid.",
  "magic_items.cljc": "Flame Tongue",
  "spell_subs.cljc": "Dwarves are short and stout",
};

/** A byte count, for the log. */
const kib = (bytes: number): string => `${bytes} bytes (${(bytes / 1024).toFixed(1)} KiB)`;

describe("the engine chunk", () => {
  for (const [file, strings] of Object.entries(excluded)) {
    it(`leaves out ${file}`, () => {
      for (const s of strings) expect(bundle.includes(s), s).toBe(false);
    });
  }

  for (const [file, s] of Object.entries(included)) {
    it(`keeps ${file} (control)`, () => {
      expect(bundle.includes(s), s).toBe(true);
    });
  }

  it("has the monsters in dist/content instead", () => {
    const monsters = readFileSync(new URL("content/monsters.json", dist), "utf8");

    for (const s of excluded["monsters.cljc"]!) expect(monsters.includes(s), s).toBe(true);
  });

  // Logs the sizes for the CI record (engine-js/README.md §Bundle size).
  it("stays under its size budget", () => {
    const raw = Buffer.byteLength(bundle);
    const gzip = gzipSync(bundle, { level: 9 }).length;
    const content = readdirSync(new URL("content/", dist)).map((file) => {
      const size = statSync(new URL(`content/${file}`, dist)).size;
      return `${file} ${kib(size)}`;
    });
    console.log(`dist/pubdoor.js ${kib(raw)}, ${kib(gzip)} gzipped`);
    console.log(`dist/content: ${content.join(", ")}`);

    expect(raw).toBeLessThan(BUDGET_BYTES);
  });
});
