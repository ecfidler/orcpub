# SRD 5.2 rules delta for a character builder

**Analyzed:** 2026-09-22
**Scope:** External facts only. This document lists what changed between SRD 5.1 (2014 rules) and SRD 5.2.1 (2024 rules) in the parts a character builder computes or offers as choices. It also records the official backward-compatibility guidance and how open-source projects (Foundry VTT `dnd5e`, 5e-bits, Open5e, Charnik) support both rule sets. It makes no design recommendation.

---

## Sources and how they were read

The session's egress proxy blocked `www.dndbeyond.com`, `media.dndbeyond.com`, `dnd.wizards.com`, `roll20.net`, `api.open5e.com`, and `www.dnd5eapi.co` for both `curl` and WebFetch. GitHub was reachable. Every SRD claim below comes from copies of the official PDFs that third-party repositories store on GitHub, read with `pypdf` and `pdfminer.six`.

| Source | Obtained from | Identity check |
|---|---|---|
| SRD 5.2.1 PDF (`SRD_CC_v5.2.1.pdf`), 364 pages | Git LFS object in `kmfarley11/ez-chars` at `docs/ext/5e2024/SRD_CC_v5.2.1.pdf` | SHA-256 `8974902d109d6e63672d7c490bde9ccf052410503d9cfa768237154fbc5e3d87`, 6,031,375 bytes. Two unrelated repositories (`sourcetrait/srdom`, `meta5by5/GMAtlas`) store an LFS pointer with the same object ID. PDF metadata: created 2025-04-23, modified 2025-04-29, title `DnD_SRD_PH_V1.indd`. Page 1 is the Wizards legal page for "SRD 5.2.1". |
| SRD 5.1 PDF, Creative Commons edition (`SRD_CC_v5.1.pdf`), 403 pages | Git LFS object in `kmfarley11/ez-chars` at `docs/ext/5e2014/SRD_CC_v5.1.pdf` | SHA-256 `2504d2a0abb0a4d491a939be4f17910a2dde0312570ab8d208080225ccf0a1f0`, 3,158,713 bytes. Page 1 is the Wizards CC-BY-4.0 legal page for SRD 5.1. |
| Foundry VTT `dnd5e` source | `github.com/foundryvtt/dnd5e`, commit `6ea7c65839ea23379c27eb36372ac573f509e7aa` (release 6.0.4, 2026-09-22) | Cloned. Release notes read on github.com. |
| `5e-bits/5e-database` | commit `e49364bb907efca149e70465be848b8790a1d5f6` (2026-09-20) | Cloned |
| `5e-bits/5e-srd-api` | commit `7455c30b5030c31b06bb4d5d9deb5cceb7e60fd7` (2026-09-22) | Cloned |
| `open5e/open5e-api` | commit `0acbf263c74caf8ad080af7a16d86d3700a1ac8f` (2026-09-22) | Sparse clone of the document and model files |
| `FernDragonborn/charnik` and `FernDragonborn/charnik-content-srd` | commits `e252a9d0e50c4bfe948af5114c11e5d9f7608fb5` and `6fde0141b330acf9bfb1f07d52b51267f79b5143` | Cloned |
| `CoolFireGiant/hewnhero-srd` | commit `d06d1dadee357857767b1e4da985df6609509bcf` | Cloned |

**Citation format.** "5.2.1 p83" means SRD 5.2.1, page 83. "5.1 p60" means SRD 5.1 (CC edition), page 60. In both PDFs the printed page number equals the PDF page index. Code citations give the path at the commit listed above.

**Not obtained.** The SRD 5.2 (5.2.0) PDF, the SRD 5.1 OGL-edition PDF, the Wizards guide "Converting to System Reference Document 5.2.1", every D&D Beyond article, and the 2024 Player's Handbook. Claims that depend on these carry the speculation marker.

**Lead source, not cited as evidence.** `OmnisGM-App/OmnisGM-Rules` holds a Markdown transcription of the conversion guide (`src/dnd/converting-srd-5.2/en/converting-to-srd-5.2.1.md`). I used it only to find items to check. Every item it suggested that appears below was verified against the SRD PDFs.

---

## A. Licensing and versions

### A1. SRD 5.2 versions and license

| Version | Release date | License | Evidence |
|---|---|---|---|
| SRD 5.2 (5.2.0) | 2025-04-22 | CC-BY-4.0 | **⚠️ UNVALIDATED SPECULATION — date and license come from search-engine summaries of `https://www.dndbeyond.com/posts/1949-you-can-now-publish-your-own-creations-using-the` and `https://www.dndbeyond.com/srd`. Both pages were blocked, so I could not read them.** |
| SRD 5.2.1 | 2025-05-01 | CC-BY-4.0 | License verified: 5.2.1 p1. Date: **⚠️ UNVALIDATED SPECULATION — from a search-engine summary of D&D Beyond pages. The PDF metadata (modified 2025-04-29) is consistent with it.** |
| Later than 5.2.1 | None found | | A web search on 2026-09-22 returned only 5.2 and 5.2.1. **⚠️ UNVALIDATED SPECULATION — absence of evidence from search results only.** |

The difference between 5.2 and 5.2.1 is reported as 15 magic items that 5.2 omitted by accident. **⚠️ UNVALIDATED SPECULATION — search-engine summary only. I had no 5.2.0 PDF to compare.**

The SRD 5.2.1 legal page names only CC-BY-4.0 and does not mention the OGL (5.2.1 p1). The required attribution statement, verbatim from 5.2.1 p1 (the PDF uses typographic quotation marks):

```text
This work includes material from the System Reference Document 5.2.1 (“SRD 5.2.1”) by Wizards of the Coast LLC, available at https://www.dndbeyond.com/srd. The SRD 5.2.1 is licensed under the Creative Commons Attribution 4.0 International License, available at https://creativecommons.org/licenses/by/4.0/legalcode.
```

The same page adds three conditions (5.2.1 p1):

- Include no other attribution to Wizards or its parent or affiliates.
- A work may state that it is "compatible with fifth edition" or "5E compatible."
- Section 5 of CC-BY-4.0 limits Wizards' liability.

### A2. SRD 5.1 license

SRD 5.1 exists under two licenses. The CC edition's legal page grants CC-BY-4.0 (5.1 p1). Its attribution statement, verbatim:

```text
This work includes material taken from the System Reference Document 5.1 (“SRD 5.1”) by Wizards of the Coast LLC and available at https://dnd.wizards.com/resources/systems-reference-document. The SRD 5.1 is licensed under the Creative Commons Attribution 4.0 International License available at https://creativecommons.org/licenses/by/4.0/legalcode.
```

Wizards announced on 2023-01-27 that all of SRD 5.1 would be available under CC-BY-4.0 in addition to OGL 1.0a, and that OGL 1.0a would stay unchanged. **⚠️ UNVALIDATED SPECULATION — search-engine summary of `https://www.dndbeyond.com/posts/1439-ogl-1-0a-creative-commons`, which was blocked. Open5e's data independently lists both `cc-by-40` and `ogl-10a` for SRD 5.1 (`data/v2/wizards-of-the-coast/srd-2014/Document.json`), but Open5e is not a primary source for licensing.**

The two attribution URLs differ. SRD 5.1 points to `dnd.wizards.com/resources/systems-reference-document`, and SRD 5.2.1 points to `www.dndbeyond.com/srd`.

---

## B. Character-building mechanics: SRD 5.1 compared with SRD 5.2.1

### B1. Character creation structure

SRD 5.1 has no step-by-step character creation procedure. It also has no standard array and no point-buy table. I searched the SRD 5.1 text for "Character Creation", "Step-by-Step", "point buy", "27 points", and "15, 14, 13, 12, 10, 8" and found none. Those rules are in the 2014 Player's Handbook, not in SRD 5.1. SRD 5.1 starts with Races (5.1 p3), then Classes, then "Beyond 1st Level" (5.1 p56).

| Topic | SRD 5.1 | SRD 5.2.1 |
|---|---|---|
| Creation steps | Not in the SRD | 1: Choose a Class. 2: Determine Origin (background, species, languages). 3: Determine Ability Scores. 4: Choose an Alignment. 5: Fill in Details (5.2.1 p19) |
| Term for ancestry | "Race", with "Subraces" (5.1 p3) | "Species" (5.2.1 p83). The word "race" does not appear anywhere in the SRD 5.2.1 text. The glossary says terms from earlier fifth edition rules are in the index (5.2.1 p176) |
| Ability score generation | Not in the SRD | Standard Array 15, 14, 13, 12, 10, 8. Random 4d6 drop lowest. Point Cost with 27 points, scores 8 to 15, costs 0,1,2,3,4,5,7,9 (5.2.1 p21) |
| Where ability increases come from | The race. Every race increases one or more scores (5.1 p3). Examples: Dwarf Con +2 (5.1 p3), Hill Dwarf Wis +1 (5.1 p4), Human all +1 (5.1 p5), Half-Elf Cha +2 plus two others +1 (5.1 p6) | The background. It lists three abilities. Increase one by 2 and a different one by 1, or all three by 1. No increase can raise a score above 20 (5.2.1 p21, p83) |
| Background step contents | Two skills, tools or languages, equipment, a feature (5.1 p60) | Ability scores, one Origin feat, two skills, one tool, equipment choice (5.2.1 p83) |
| Level 1 HP | "Hit Points at 1st Level" in each class (5.1 p8, p11, p15, p19, p24, p26, p30, p35, p39, p42, p46, p52) | Barbarian 12. Fighter, Paladin, Ranger 10. Bard, Cleric, Druid, Monk, Rogue, Warlock 8. Sorcerer, Wizard 6. Each adds the Con modifier (5.2.1 p22). The values match SRD 5.1 |
| Fixed HP per level | "Hit Points at Higher Levels" in each class, for example 1d12 (or 7) for the Barbarian (5.1 p8) | 7, 6, 5, 4 + Con for the same four groups (5.2.1 p23). The values match SRD 5.1 |
| Starting above level 1 | Not in the SRD | Start with the minimum XP for the level. The Starting Equipment at Higher Levels table adds gold and magic items (5.2.1 p24) |
| Bonus feats after level 20 | Not in the SRD | Optional. One feat per 30,000 XP above 355,000 (5.2.1 p24) |

### B2. Backgrounds

**What a 5.2 background grants** (5.2.1 p83):

- Three listed abilities for the +2/+1 or +1/+1/+1 increase.
- One specified Origin feat.
- Proficiency in two specified skills.
- Proficiency with one tool, either a specific tool or one chosen from a category.
- A choice between an equipment package and 50 GP.

A 5.2 background grants no language and no "feature". Languages are a separate origin step (see B4).

| Background | Ability scores | Origin feat | Skills | Tool | Equipment option A (option B is 50 GP) |
|---|---|---|---|---|---|
| Acolyte | Int, Wis, Cha | Magic Initiate (Cleric) | Insight, Religion | Calligrapher's Supplies | Calligrapher's Supplies, Book (prayers), Holy Symbol, Parchment (10 sheets), Robe, 8 GP |
| Criminal | Dex, Con, Int | Alert | Sleight of Hand, Stealth | Thieves' Tools | 2 Daggers, Thieves' Tools, Crowbar, 2 Pouches, Traveler's Clothes, 16 GP |
| Sage | Con, Int, Wis | Magic Initiate (Wizard) | Arcana, History | Calligrapher's Supplies | Quarterstaff, Calligrapher's Supplies, Book (history), Parchment (8 sheets), Robe, 8 GP |
| Soldier | Str, Dex, Con | Savage Attacker | Athletics, Intimidation | One kind of Gaming Set | Spear, Shortbow, 20 Arrows, Gaming Set (same as above), Healer's Kit, Quiver, Traveler's Clothes, 14 GP |

Source: 5.2.1 p83.

**SRD 5.1** has one background, Acolyte: Insight and Religion, two languages of your choice, an equipment package with 15 gp, and the Shelter of the Faithful feature (5.1 p60 to p61). It grants no ability scores and no feat. SRD 5.1 also has "Customizing a Background", which allows replacing the feature and choosing any two skills and any two tools or languages (5.1 p60).

SRD 5.2.1 has a GM procedure, "Creating a Background": choose three abilities, one Origin feat, two skills, one tool, and a 50 GP package with no Martial weapons or armor (5.2.1 p192 to p193).

### B3. Species

**Parts of a 5.2 species** (5.2.1 p83 to p84): creature type, size, speed, and special traits. Every species in SRD 5.2.1 is Humanoid, and "playable non-Humanoid species appear in other books" (5.2.1 p83). A 5.2 species has no ability score increase and no language entry. SRD 5.1 races give a fixed size and languages (5.1 p3 to p7) and state no creature type.

| Species | SRD 5.1 | SRD 5.2.1 size | 5.2.1 speed | 5.2.1 sub-choice | Source |
|---|---|---|---|---|---|
| Dragonborn | Race, Medium, 30 ft, Draconic Ancestry choice | Medium | 30 | Draconic Ancestry: 10 dragons, each with a damage type | 5.2.1 p84 |
| Dwarf | Race with subrace Hill Dwarf, 25 ft | Medium | 30 | None. Dwarven Toughness is now base (it was Hill Dwarf's in 5.1, 5.1 p4) | 5.2.1 p84 |
| Elf | Race with subrace High Elf | Medium | 30 | Elven Lineage: Drow, High Elf, or Wood Elf. Spells at character levels 3 and 5. Choose Int, Wis, or Cha as the spellcasting ability | 5.2.1 p84 to p85 |
| Gnome | Race with subrace Rock Gnome, Small, 25 ft | Small | 30 | Gnomish Lineage: Forest Gnome or Rock Gnome. Choose Int, Wis, or Cha | 5.2.1 p85 |
| Goliath | Not in SRD 5.1 | Medium | 35 | Giant Ancestry: 6 options (Cloud, Fire, Frost, Hill, Stone, Storm) | 5.2.1 p85 to p86 |
| Halfling | Race with subrace Lightfoot, Small, 25 ft | Small | 30 | None | 5.2.1 p86 |
| Human | Race, Medium, all abilities +1, Common plus one language | Medium or Small, chosen | 30 | Skillful (one skill) and Versatile (one Origin feat, "Skilled is recommended") | 5.2.1 p86 |
| Orc | Not in SRD 5.1 (SRD 5.1 has Half-Orc) | Medium | 30 | None | 5.2.1 p86 |
| Tiefling | Race, Medium, Common and Infernal | Medium or Small, chosen | 30 | Fiendish Legacy: Abyssal, Chthonic, or Infernal. Spells at levels 3 and 5. Choose Int, Wis, or Cha | 5.2.1 p86 |
| Half-Elf | Race (5.1 p6) | Not in SRD 5.2.1 | | | 5.2.1 p20 lists nine species |
| Half-Orc | Race (5.1 p7) | Not in SRD 5.2.1 | | | 5.2.1 p20 |

Structural points in the 5.2 species rules:

- **Species can grant a feat.** Human Versatile grants an Origin feat of the player's choice (5.2.1 p86).
- **Traits scale with character level**, not class level. Examples: Breath Weapon damage at levels 5, 11, and 17, Draconic Flight at 5, lineage spells at 3 and 5, and Goliath Large Form at 5 (5.2.1 p84 to p86).
- **Uses per rest equal the Proficiency Bonus** for many traits: Breath Weapon, Stonecunning, Giant Ancestry, and Adrenaline Rush (5.2.1 p84 to p86).
- **A per-species spellcasting ability choice** is made when the player selects the lineage or legacy (5.2.1 p85, p86).
- **Size is a choice** for Human and Tiefling (5.2.1 p86).
- **Human grants Heroic Inspiration** on each Long Rest (5.2.1 p86).

### B4. Languages

| | SRD 5.1 | SRD 5.2.1 |
|---|---|---|
| Source of languages | "Your race indicates the languages your character can speak by default, and your background might give you access to one or more additional languages" (5.1 p59). Each race lists fixed languages, for example Dwarf: Common and Dwarvish (5.1 p3). Human and Half-Elf each add one of choice (5.1 p5, p6). Acolyte adds two (5.1 p61) | Every character knows Common plus two languages rolled or chosen from the Standard Languages table. "Your class and other features might also give you languages" (5.2.1 p20) |
| Standard languages | Common, Dwarvish, Elvish, Giant, Gnomish, Goblin, Halfling, Orc (5.1 p59) | Common, Common Sign Language, Draconic, Dwarvish, Elvish, Giant, Gnomish, Goblin, Halfling, Orc (5.2.1 p20) |
| Exotic or rare languages | Exotic: Abyssal, Celestial, Draconic, Deep Speech, Infernal, Primordial, Sylvan, Undercommon. Thieves' cant and Druidic are "secret" languages (5.1 p59) | Rare: Abyssal, Celestial, Deep Speech, Druidic, Infernal, Primordial, Sylvan, Thieves' Cant, Undercommon (5.2.1 p20) |
| Class-granted languages | Druidic (Druid), Thieves' Cant (Rogue) | Druidic (5.2.1 p42), Thieves' Cant plus one language of choice (5.2.1 p62), Ranger Deft Explorer: two languages at level 2 (5.2.1 p59) |

Draconic moved from exotic to standard. Common Sign Language is new. Foundry's data migration encodes the same moves (see D1).

### B5. Feats

| | SRD 5.1 | SRD 5.2.1 |
|---|---|---|
| Feat list | Grappler only (5.1 p75) | 17 feats in four categories (5.2.1 p87 to p88) |
| How feats are gained | An optional rule: forgo an Ability Score Improvement to take a feat (5.1 p75) | Backgrounds grant an Origin feat. Class "Ability Score Improvement" features grant the Ability Score Improvement feat "or another feat of your choice for which you qualify" (for example 5.2.1 p29) |
| Categories | None | Origin, General, Fighting Style, Epic Boon. "If you're instructed to choose a feat and no category is specified, you can choose from any category" (5.2.1 p87) |
| Prerequisites | Grappler: Strength 13 (5.1 p75) | Level (for example "Level 4+"), ability score, a class feature ("Fighting Style Feature", "Spellcasting Feature"). "If a prerequisite includes a class, you must have at least 1 level in that class" (5.2.1 p87) |
| Repeatable | "You can take each feat only once, unless the feat's description says otherwise" (5.1 p75) | Only if the feat has a "Repeatable" subsection (5.2.1 p87) |
| Ability score cap | 20 | ASI feat and Grappler cap at 20. Epic Boon increases cap at 30 (5.2.1 p87 to p88) |

SRD 5.2.1 feats, verified at 5.2.1 p87 to p88:

| Category | Feat | Prerequisite | Ability increase | Repeatable |
|---|---|---|---|---|
| Origin | Alert | None | None | No |
| Origin | Magic Initiate | None | None. Choose Cleric, Druid, or Wizard list, and Int, Wis, or Cha as the spellcasting ability | Yes, with a different spell list each time |
| Origin | Savage Attacker | None | None | No |
| Origin | Skilled | None | None. Three skills or tools in any combination | Yes |
| General | Ability Score Improvement | Level 4+ | +2 to one or +1 to two, max 20 | Yes |
| General | Grappler | Level 4+, Str or Dex 13+ | Str or Dex +1, max 20 | No |
| Fighting Style | Archery | Fighting Style Feature | None | No |
| Fighting Style | Defense | Fighting Style Feature | None | No |
| Fighting Style | Great Weapon Fighting | Fighting Style Feature | None | No |
| Fighting Style | Two-Weapon Fighting | Fighting Style Feature | None | No |
| Epic Boon | Boon of Combat Prowess | Level 19+ | Any one +1, max 30 | No |
| Epic Boon | Boon of Dimensional Travel | Level 19+ | Any one +1, max 30 | No |
| Epic Boon | Boon of Fate | Level 19+ | Any one +1, max 30 | No |
| Epic Boon | Boon of Irresistible Offense | Level 19+ | Str or Dex +1, max 30 | No |
| Epic Boon | Boon of Spell Recall | Level 19+, Spellcasting Feature | Int, Wis, or Cha +1, max 30 | No |
| Epic Boon | Boon of the Night Spirit | Level 19+ | Any one +1, max 30 | No |
| Epic Boon | Boon of Truesight | Level 19+ | Any one +1, max 30 | No |

Other feat sources in 5.2.1: the Warlock invocation Lessons of the First Ones grants an Origin feat and is repeatable with a different feat each time (5.2.1 p73). The Champion's Additional Fighting Style grants a second Fighting Style feat (5.2.1 p49).

### B6. Classes

**Subclass level.** Every class gains its subclass at level 3 in SRD 5.2.1. Each class has a "Level 3: [Class] Subclass" feature (5.2.1 p29, p32, p37, p43, p48, p51, p55, p59, p62, p66, p72, p78). In SRD 5.1, the Cleric domain is chosen at level 1 (5.1 p16), the Sorcerer origin at level 1 (5.1 p43), the Warlock patron at level 1 (5.1 p46), the Druid circle at level 2 (5.1 p21), and the Wizard tradition at level 2 (5.1 p53). The other seven classes choose at level 3 in SRD 5.1 (5.1 p9, p12, p25, p27, p32, p37, p40).

**Level 19.** Every 5.2.1 class has "Level 19: Epic Boon", which grants an Epic Boon feat or another feat (5.2.1 p30, p33, p38, p43, p48, p52, p55, p59, p63, p66, p72, p79). In SRD 5.1, level 19 is an Ability Score Improvement for every class (for example 5.1 p9, p25, p40).

**Class multiclass entry.** Each 5.2.1 class has an "As a Multiclass Character" list of the traits gained (for example 5.2.1 p28). SRD 5.1 uses one Multiclassing Proficiencies table.

| Class | SRD 5.1 subclass, level | SRD 5.2.1 subclass, level | Changes that affect the data model or choice tree (5.2.1) |
|---|---|---|---|
| Barbarian | Path of the Berserker, 3 | Path of the Berserker, 3 (p30) | Weapon Mastery: 2 kinds at 1, 3 at 4, 4 at 10, and one choice can change per Long Rest (p28 to p29). Primal Knowledge at 3 adds one skill (p29). Brutal Strike at 9, more options at 13 and 17 (p29 to p30). Primal Champion: Str and Con +4, max 25 (p30). SRD 5.1's max is 24 (5.1 p9) |
| Bard | College of Lore, 3 | College of Lore, 3 (p35) | Prepared Spells column replaces Spells Known. Swap one spell per Bard level (p32). Expertise at 2 and 9 (p32). SRD 5.1 grants Expertise at 3 (5.1 p11). Magical Secrets at 10 lets any newly prepared spell come from the Bard, Cleric, Druid, or Wizard list (p33). SRD 5.1 grants two spells from any class at 10, 14, and 18 (5.1 p13) |
| Cleric | Life Domain, 1 | Life Domain, 3 (p40) | Divine Order choice at 1: Protector (Martial weapons, Heavy armor) or Thaumaturge (extra cantrip, bonus to Arcana or Religion) (p37). Channel Divinity: 2 uses at 2, 3 at 6, 4 at 18, regain one on a Short Rest (p36 to p37). SRD 5.1: 1/rest, 2/rest, 3/rest (5.1 p15). Blessed Strikes choice at 7 (Divine Strike or Potent Spellcasting) (p38). Prepared Spells column replaces Wisdom modifier + Cleric level (5.1 p16) |
| Druid | Circle of the Land, 2 | Circle of the Land, 3 (p46) | Primal Order choice at 1: Magician or Warden (Martial weapons, Medium armor) (p42). Wild Shape: a list of known forms (4 at 2, 6 at 4, 8 at 8) that can change on a Long Rest, Temporary HP equal to Druid level, and the character keeps its own HP (p42 to p43). SRD 5.1 replaces HP with the beast's and allows any beast seen (5.1 p20). Druidic grants Speak with Animals always prepared (p42). Wild Companion at 2 (p43). Elemental Fury choice at 7 (p43). Prepared Spells column replaces Wisdom modifier + Druid level (5.1 p20) |
| Fighter | Champion, 3 | Champion, 3 (p49) | Fighting Style is a Fighting Style feat, replaceable on each Fighter level (p47). Weapon Mastery: 3 kinds at 1, 4 at 4, 5 at 10, 6 at 16 (p47 to p48). Second Wind uses: 2, 3, 4 (p47). Tactical Master at 9 swaps a mastery property for Push, Sap, or Slow (p48). Champion Additional Fighting Style at 7 and Heroic Warrior at 10 (p49) |
| Monk | Way of the Open Hand, 3 | Warrior of the Open Hand, 3 (p52) | Martial Arts die 1d6, 1d8 at 5, 1d10 at 11, 1d12 at 17 (p50). SRD 5.1: 1d4, 1d6, 1d8, 1d10 (5.1 p26). Ki is renamed Focus Points (p50). Monk weapons are Simple Melee weapons and Martial Melee weapons with the Light property (p50). SRD 5.1: shortswords and simple melee weapons without Two-Handed or Heavy (5.1 p26). Body and Mind at 20: Dex and Wis +4, max 25 (p52) |
| Paladin | Oath of Devotion, 3 | Oath of Devotion, 3 (p56) | Spellcasting at level 1 with 2 slots (p53 to p54). SRD 5.1: Spellcasting at 2 (5.1 p31). Prepared Spells column replaces Charisma modifier + half Paladin level (5.1 p31). Replace one prepared spell per Long Rest (p54). Weapon Mastery: 2 kinds, changeable on a Long Rest (p54). Fighting Style at 2: a Fighting Style feat or Blessed Warrior (two Cleric cantrips) (p54). Divine Smite is a spell. Paladin's Smite keeps it always prepared with one free cast per Long Rest (p54, p125). SRD 5.1: Divine Smite is a class feature that spends a slot (5.1 p31). Channel Divinity at 3: 2 uses, 3 at 11 (p54) |
| Ranger | Hunter, 3 | Hunter, 3 (p61) | Spellcasting at 1 (p57). SRD 5.1: at 2 (5.1 p36). Prepared Spells column replaces Spells Known (5.1 p36). Replace one per Long Rest (p58). Favored Enemy: Hunter's Mark always prepared with free casts 2 to 6 by level (p58). SRD 5.1 Favored Enemy and Natural Explorer choose a creature type and a terrain (5.1 p35 to p36). Weapon Mastery 2 kinds (p58 to p59). Deft Explorer at 2: one Expertise and two languages (p59). Fighting Style at 2: a feat or Druidic Warrior (p59). Expertise at 9 (p59) |
| Rogue | Thief, 3 | Thief, 3 (p64) | Weapon proficiency: Simple, plus Martial weapons with Finesse or Light (p61). Weapon Mastery 2 kinds (p62). Thieves' Cant plus one language (p62). Steady Aim at 3 (p62). Cunning Strike at 5: spend Sneak Attack dice on effects, two effects at 11, more options at 14 (p63). Slippery Mind grants Wisdom and Charisma saves (p63). SRD 5.1: Wisdom only (5.1 p40). ASI at 4, 8, 10, 12, 16 (p63) |
| Sorcerer | Draconic Bloodline, 1 | Draconic Sorcery, 3 (p69) | Innate Sorcery at 1 (p65 to p66). Metamagic at 2: 2 options, 2 more at 10, 2 more at 17, and one can be replaced per Sorcerer level (p66). SRD 5.1: at 3, 2 options, +1 at 10 and +1 at 17 (5.1 p44). Metamagic list adds Seeking Spell and Transmuted Spell (p66 to p67). Costs change: Heightened Spell 2 points (SRD 5.1: 3), Twinned Spell 1 point (SRD 5.1: the spell's level) (5.1 p44). Sorcerous Restoration moves to 5 (p66). SRD 5.1 has it at 20 (5.1 p44). Prepared Spells column replaces Spells Known (5.1 p43) |
| Warlock | The Fiend, 1 | Fiend Patron, 3 (p76) | Eldritch Invocations start at 1: 1 at 1, 3 at 2, rising to 10 at 20 (p71). SRD 5.1: 2 at 2, rising to 8 at 20 (5.1 p46 to p47). Pact of the Blade, Pact of the Chain, and Pact of the Tome are invocations (p73 to p74). SRD 5.1: a separate Pact Boon at 3 (5.1 p47). Invocation prerequisites can be a Warlock level, another invocation, or "a Warlock Cantrip That Deals Damage" (p72 to p74). Some invocations are repeatable (Agonizing Blast, Eldritch Spear, Lessons of the First Ones, Repelling Blast) (p72 to p74). An invocation that is a prerequisite for another cannot be replaced (p71). Magical Cunning at 2, Contact Patron at 9 (p72) |
| Wizard | School of Evocation, 2 | Evoker, 3 (p82) | Scholar at 2: Expertise in one of Arcana, History, Investigation, Medicine, Nature, or Religion (p78). Prepared Spells column replaces Intelligence modifier + Wizard level (5.1 p53). One cantrip can be replaced on each Long Rest (p77). Memorize Spell at 5: swap one prepared spell on a Short Rest (p79). Ritual Adept keeps ritual casting from the spellbook (p78) |

The SRD 5.2.1 subclass set is the same set of twelve as SRD 5.1, with four renames: Warrior of the Open Hand, Draconic Sorcery, Fiend Patron, and Evoker.

One 5.2.1 feature carries its own compatibility note. Blessed Strikes says: "if you get either option from a Cleric subclass in an older book, use only the option you choose for this feature" (5.2.1 p38).

### B7. Spellcasting

| Topic | SRD 5.1 | SRD 5.2.1 |
|---|---|---|
| Known or prepared | Bard, Ranger, Sorcerer, and Warlock know spells (5.1 p12, p36, p43, p47). Cleric, Druid, Paladin, and Wizard prepare a number equal to ability modifier + level, or + half level for the Paladin (5.1 p16, p20, p31, p53) | Every class with Spellcasting or Pact Magic has a Prepared Spells column with a fixed number per level (5.2.1 p31, p36, p41, p53, p58, p65, p71, p77). The count no longer depends on an ability modifier |
| When the list changes | Known casters replace one spell on gaining a class level (for example 5.1 p12). Prepared casters change the list after a long rest (for example 5.1 p16) | Spell Preparation by Class table: Bard, Sorcerer, Warlock change one on gaining a level. Cleric, Druid, Wizard change any after a Long Rest. Paladin, Ranger change one after a Long Rest (5.2.1 p104) |
| Always-prepared spells | Class-specific, for example Cleric domain spells are always prepared and don't count against the list (5.1 p16) | A general rule. An always-prepared spell does not count against the list (5.2.1 p104) |
| Cantrips known | Bard 2/3/4, Cleric 3/4/5, Druid 2/3/4, Sorcerer 4/5/6, Warlock 2/3/4, Wizard 3/4/5 at levels 1/4/10 (5.1 class tables p11, p15, p19, p42, p46, p52) | The same counts (5.2.1 p31, p36, p41, p65, p71, p77). New: the Bard, Cleric, Druid, Sorcerer, and Warlock can replace one cantrip on each class level. The Wizard can replace one after each Long Rest (p32, p36, p42, p64, p71, p77). Divine Order Thaumaturge and Primal Order Magician add one cantrip (p37, p42) |
| Full-caster slots | Class tables (5.1 p11, p15, p19, p42, p52) | The same values in every row I compared: levels 1, 2, 4, 5, 10, 11, 17, and 20 for the Bard, Cleric, Druid, Sorcerer, and Wizard (5.2.1 p31, p36, p41, p65, p77) |
| Half-caster slots | Paladin and Ranger have no slots at level 1. At level 2 they have two level 1 slots (5.1 p30, p35) | Two level 1 slots at level 1. From level 2 on, the rows I compared match SRD 5.1 (5.2.1 p53, p58) |
| Pact Magic slots | 1 slot at 1, 2 at 2, 3 at 11, 4 at 17, slot level 1 to 5 (5.1 p46) | The same (5.2.1 p71). The SRD 5.1 Spells Known column and the SRD 5.2.1 Prepared Spells column also match in the rows I compared (levels 1, 2, 4, 5, 10, 11, 17, 20), running from 2 to 15 |
| Ritual casting | Per class feature. Bard casts known rituals, Cleric and Druid cast prepared rituals, Wizard casts from the spellbook (5.1 p12, p16, p20, p53) | A general rule: the caster must have the spell prepared (5.2.1 p104). The Wizard's Ritual Adept keeps spellbook rituals (5.2.1 p78) |
| Spells per turn | After casting a bonus action spell, "You can't cast another spell during the same turn, except for a cantrip with a casting time of 1 action" (5.1 p101) | "On a turn, you can expend only one spell slot to cast a spell" (5.2.1 p105) |
| Casting in armor | Without proficiency in the worn armor, "you can't cast spells" (5.1 p62) | The same rule, now phrased as armor training (5.2.1 p92, p104) |
| Multiclass slot level | All levels of Bard, Cleric, Druid, Sorcerer, Wizard, plus half (rounded down) of Paladin and Ranger (5.1 p58) | The same classes, but half of Paladin and Ranger levels rounded up (5.2.1 p25). The Multiclass Spellcaster table is the same as SRD 5.1's (5.2.1 p26) |
| Multiclass spell selection | Known and prepared per class (5.1 p58) | Prepared per class. Each prepared spell is tied to one class and uses that class's ability (5.2.1 p25) |
| Pact Magic in multiclass | Pact slots and Spellcasting slots can cast each other's spells (5.1 p58) | The same (5.2.1 p26) |
| Cantrip scaling in multiclass | Not stated in the SRD 5.1 multiclass rules (5.1 p57 to p58) | By total character level unless the spell says otherwise (5.2.1 p25) |

### B8. Spells

**Counts.** SRD 5.1 has 319 spells and SRD 5.2.1 has 339. I parsed every spell header in both PDFs (5.1 p114 to p193, 5.2.1 p107 to p175). The `5e-bits/5e-database` data has the same counts: 319 in `src/2014/en/5e-SRD-Spells.json` and 339 in `src/2024/en/5e-SRD-Spells.json`. My 5.1 name list matches 5e-database's exactly.

**Renamed** (2): Feeblemind is now Befuddlement (level 8 Enchantment, 5.2.1 p112). Branding Smite is now Shining Smite (level 2, 5.2.1 p162). These are the only two SRD 5.1 names absent from SRD 5.2.1. Feeblemind and Befuddlement are both level 8 Enchantment. Branding Smite is level 2 Evocation and Shining Smite is level 2 Transmutation. The conversion-guide transcription lists both pairs as renames. I could not confirm that against the official guide.

**New in SRD 5.2.1** (20): Aura of Life, Charm Monster, Chromatic Orb, Dissonant Whispers, Divine Smite, Dragon's Breath, Elementalism, Ensnaring Strike, Hex, Ice Knife, Mind Spike, Phantasmal Force, Power Word Heal, Ray of Sickness, Searing Smite, Sorcerous Burst, Starry Wisp, Summon Dragon, Tsunami, Vitriolic Sphere.

**School changed, same name and level** (23 spells). No shared spell changed level.

| Spell | 5.1 school | 5.2.1 school |
|---|---|---|
| Acid Splash | Conjuration | Evocation |
| Blindness/Deafness | Necromancy | Transmutation |
| Contingency | Evocation | Abjuration |
| Cure Wounds, Healing Word, Mass Cure Wounds, Mass Healing Word, Prayer of Healing, Heal, Mass Heal | Evocation | Abjuration |
| Dancing Lights | Evocation | Illusion |
| Divine Favor | Evocation | Transmutation |
| Earthquake | Evocation | Transmutation |
| Etherealness | Transmutation | Conjuration |
| Giant Insect | Transmutation | Conjuration |
| Glibness | Transmutation | Enchantment |
| Goodberry | Transmutation | Conjuration |
| Hallow | Evocation | Abjuration |
| Poison Spray | Conjuration | Necromancy |
| Reincarnate | Transmutation | Necromancy |
| Resilient Sphere | Evocation | Abjuration |
| Sending | Evocation | Divination |
| Stoneskin | Abjuration | Transmutation |

Source: spell headers in both PDFs. The 5.2.1 page for each spell is in the header. For example, Cure Wounds is at 5.1 p132 and 5.2.1 p121.

**Same name, different mechanics** (verified examples):

| Spell | SRD 5.1 | SRD 5.2.1 |
|---|---|---|
| Cure Wounds | 1d8 + modifier, +1d8 per slot level (5.1 p132) | 2d8 + modifier, +2d8 per slot level (5.2.1 p121) |
| Healing Word | 1d4 + modifier, no effect on undead or constructs (5.1 p153) | 2d4 + modifier, +2d4 per slot level. The undead and construct exclusion is gone (5.2.1 p139) |
| Hunter's Mark | +1d6 damage on weapon attacks. Move the mark with a bonus action on a later turn (5.1 p154) | +1d6 Force damage on any attack roll hit (5.2.1 p141) |
| True Strike | Concentration, advantage on the first attack next turn (5.1 p189) | Make one attack with a weapon using the spellcasting ability, optional Radiant damage, extra Radiant damage at levels 5, 11, 17 (5.2.1 p170) |
| Divine Smite | A Paladin class feature. Spend a slot on a melee weapon hit for 2d8 +1d8 per slot level (5.1 p31) | A level 1 Paladin spell, Bonus Action after a hit, 2d8 Radiant +1d8 per slot level (5.2.1 p125) |

**Class spell lists changed.** SRD 5.2.1 tags each spell description with its classes, for example "Level 2 Abjuration (Bard, Cleric, Druid, Paladin, Ranger)" (5.2.1 p107). SRD 5.1 keeps class lists in a separate Spell Lists chapter (5.1 p105 to p113). The table compares the SRD 5.1 lists with the SRD 5.2.1 description tags. The rename pairs appear as one addition and one removal.

| Class | 5.1 count | 5.2.1 count | Added in 5.2.1 | Removed |
|---|---|---|---|---|
| Bard | 112 | 130 | Aid, Antipathy/Sympathy, Befuddlement, Charm Monster, Color Spray, Command, Dissonant Whispers, Enlarge/Reduce, Heroes' Feast, Mass Healing Word, Mirror Image, Phantasmal Force, Phantasmal Killer, Power Word Heal, Prismatic Spray, Prismatic Wall, Slow, Starry Wisp, Telepathic Bond | Feeblemind |
| Cleric | 105 | 109 | Aura of Life, Power Word Heal, Sunbeam, Sunburst | None |
| Druid | 105 | 124 | Aid, Augury, Befuddlement, Charm Monster, Cone of Cold, Continual Flame, Divination, Elementalism, Enlarge/Reduce, Fire Shield, Flesh to Stone, Ice Knife, Incendiary Cloud, Message, Protection from Evil and Good, Revivify, Spare the Dying, Starry Wisp, Symbol, Tsunami | Feeblemind |
| Paladin | 31 | 38 | Aura of Life, Divine Smite, Gentle Repose, Greater Restoration, Prayer of Healing, Searing Smite, Shining Smite, Warding Bond | Branding Smite |
| Ranger | 37 | 48 | Aid, Dispel Magic, Dominate Beast, Enhance Ability, Ensnaring Strike, Entangle, Greater Restoration, Gust of Wind, Magic Weapon, Meld into Stone, Revivify | None |
| Sorcerer | 120 | 140 | Arcane Hand, Charm Monster, Chromatic Orb, Demiplane, Dragon's Breath, Elementalism, Fire Shield, Flame Blade, Flaming Sphere, Flesh to Stone, Freezing Sphere, Grease, Ice Knife, Magic Weapon, Mind Spike, Phantasmal Force, Ray of Sickness, Sorcerous Burst, Vampiric Touch, Vitriolic Sphere | None |
| Warlock | 64 | 72 | Bane, Befuddlement, Charm Monster, Detect Magic, Gate, Hex, Hideous Laughter, Mind Spike, Mislead, Planar Binding, Speak with Animals, Teleportation Circle, Weird | Conjure Fey, Feeblemind, Flesh to Stone, Mass Suggestion, Shatter |
| Wizard | 204 | 218 | Augury, Befuddlement, Charm Monster, Chromatic Orb, Divination, Dragon's Breath, Elementalism, Enhance Ability, Ice Knife, Mind Spike, Phantasmal Force, Ray of Sickness, Speak with Dead, Summon Dragon, Vitriolic Sphere | Feeblemind |

**Source inconsistency.** Mind Spike's description tags it "(Sorcerer, Warlock, Wizard)" (5.2.1 p149), but the Sorcerer Spell List table on 5.2.1 p67 to p68 does not include it. 5e-database's 2024 data follows the description tag.

### B9. Equipment

**Weapon Mastery properties** (5.2.1 p90): Cleave, Graze, Nick, Push, Sap, Slow, Topple, Vex. Each weapon has exactly one. A character uses it only with a feature such as Weapon Mastery (5.2.1 p89). Classes with Weapon Mastery: Barbarian, Fighter, Paladin, Ranger, Rogue (see B6). Fighter Tactical Master can substitute Push, Sap, or Slow (5.2.1 p48).

Mastery by weapon (5.2.1 p91):

| Mastery | Weapons |
|---|---|
| Cleave | Greataxe, Halberd |
| Graze | Glaive, Greatsword |
| Nick | Dagger, Light Hammer, Sickle, Scimitar |
| Push | Greatclub, Pike, Warhammer, Heavy Crossbow |
| Sap | Mace, Spear, Flail, Longsword, Morningstar, War Pick |
| Slow | Club, Javelin, Light Crossbow, Sling, Whip, Longbow, Musket |
| Topple | Quarterstaff, Battleaxe, Lance, Maul, Trident |
| Vex | Handaxe, Dart, Shortbow, Rapier, Shortsword, Blowgun, Hand Crossbow, Pistol |

**Weapon table changes** (5.1 p65 to p66 compared with 5.2.1 p91):

| Weapon | SRD 5.1 | SRD 5.2.1 |
|---|---|---|
| Lance | 1d12 piercing, Reach, Special | 1d10 Piercing, Heavy, Reach, Two-Handed (unless mounted) |
| Trident | 1d6, Thrown (20/60), Versatile (1d8) | 1d8, Thrown (20/60), Versatile (1d10) |
| War Pick | 1d8, no properties | 1d8, Versatile (1d10) |
| Warhammer | 2 lb. | 5 lb. |
| Net | Martial ranged weapon, Special, Thrown (5/15) | Not a weapon. Adventuring gear, 3 lb., 1 GP (5.2.1 p95) |
| Musket, Pistol | Not in SRD 5.1 | New martial ranged weapons, 500 GP and 250 GP |
| Ammunition | "Ammunition (range 80/320)" | The type is part of the property, for example "Ammunition (Range 80/320; Bolt)" |
| Names | "Crossbow, light", "Crossbow, hand", "Crossbow, heavy" | Light Crossbow, Hand Crossbow, Heavy Crossbow |

**Property rule changes.** Heavy in SRD 5.1 gives Small creatures disadvantage (5.1 p65). In SRD 5.2.1, Heavy gives Disadvantage if Strength is below 13 for a melee weapon or Dexterity is below 13 for a ranged weapon (5.2.1 p89). Light in SRD 5.2.1 contains the extra Bonus Action attack rule (5.2.1 p89).

**Armor.** AC, Strength requirement, Stealth, weight, and cost values are the same in both tables (5.1 p64, 5.2.1 p92). Differences:

- Names gain "Armor": Padded Armor, Leather Armor, Studded Leather Armor, Hide Armor, Half Plate Armor, Splint Armor, Plate Armor (5.2.1 p92).
- A Shield takes the Utilize action to don or doff (5.2.1 p92). SRD 5.1: 1 action (5.1 p64).
- "Armor training" replaces "armor proficiency". Without training, a creature has Disadvantage on D20 Tests that involve Strength or Dexterity and cannot cast spells (5.2.1 p92).

**Tools** (5.2.1 p93). Each tool lists an Ability, a Utilize entry with DCs, a Craft entry, and sometimes Variants that each need separate proficiency. Proficiency with both the tool and the relevant skill gives Advantage on the check (5.2.1 p93). Crafting rules for nonmagical items, Potions of Healing, and Spell Scrolls are at 5.2.1 p103.

**Starting equipment structure.**

- SRD 5.1: the class lists item-by-item choices, for example "(a) a greataxe or (b) any martial melee weapon" (5.1 p8), in addition to the background's package (5.1 p60).
- SRD 5.2.1: the class offers whole packages. Every class except the Fighter offers "Choose A or B", with B as gold, for example Barbarian "(A) Greataxe, 4 Handaxes, Explorer's Pack, and 15 GP; or (B) 75 GP" (5.2.1 p28). The Fighter offers A, B, or C, with C as 155 GP (5.2.1 p47). Backgrounds offer a package or 50 GP (5.2.1 p83). Coins gained here can be spent immediately (5.2.1 p20).

### B10. Rules glossary items a builder shows

| Item | SRD 5.1 | SRD 5.2.1 |
|---|---|---|
| Exhaustion | Six levels with cumulative effects: 1 disadvantage on ability checks, 2 speed halved, 3 disadvantage on attacks and saves, 4 HP maximum halved, 5 speed 0, 6 death (5.1 p358) | Cumulative levels. Each D20 Test is reduced by 2 × the level. Speed is reduced by 5 × the level. Death at level 6. A Long Rest removes 1 level (5.2.1 p181) |
| Inspiration | "Inspiration", awarded by the GM, grants advantage on one attack, save, or check (5.1 p59 to p60) | "Heroic Inspiration": reroll any die and use the new roll. A character can have only one. Humans gain it on each Long Rest (5.2.1 p8, p183, p86) |
| D20 Test | No umbrella term | The umbrella term for ability checks, attack rolls, and saving throws (5.2.1 p6) |
| Multiclass prerequisites | A table: 13 in named abilities. Fighter needs Str 13 or Dex 13. Monk and Ranger need Dex 13 and Wis 13 (5.1 p56) | "At least 13 in the primary ability of the new class and your current classes" (5.2.1 p24). The primary abilities are in the Class Overview table (5.2.1 p19) |
| Grappling | A grapple check: Athletics contested by Athletics or Acrobatics (5.1 p95) | An Unarmed Strike option. The target makes a Strength or Dexterity save against DC 8 + Str modifier + Proficiency Bonus (5.2.1 p190) |
| Grappled condition | Speed 0, and no bonus to speed applies (5.1 p358) | Speed 0. Disadvantage on attacks against any target other than the grappler (5.2.1 p182) |
| Conditions named | Blinded, Charmed, Deafened, Exhaustion, Frightened, Grappled, Incapacitated, Invisible, Paralyzed, Petrified, Poisoned, Prone, Restrained, Stunned, Unconscious (5.1 p358 to p359) | The same names, with revised effects (5.2.1 p179 and following entries) |
| Long Rest | Regain spent Hit Dice up to half the character's total, minimum one (5.1 p87) | Regain all HP and all spent Hit Point Dice. Exhaustion decreases by 1 (5.2.1 p185) |
| Concentration DC | 10 or half the damage, whichever is higher (5.1 p102) | 10 or half the damage, with a maximum DC of 30 (5.2.1 p179) |
| Bloodied | No such term | A creature with half its HP or fewer (5.2.1 p177). The Champion's Heroic Rally uses it (5.2.1 p49) |

### B11. Other computed values

| Value | SRD 5.1 | SRD 5.2.1 |
|---|---|---|
| Armor Class | Class features give alternative formulas. The multiclass rules say only that a character "can't gain" Unarmored Defense again from another class (5.1 p57) | Base 10 + Dex. If a rule gives another base calculation, choose one (5.2.1 p177, p25) |
| Initiative | A Dexterity check (5.1 p80) | A Dexterity check (5.2.1 p13). Alert adds the Proficiency Bonus (5.2.1 p87). New Initiative score option: 10 + Dex, +5 with Advantage, -5 with Disadvantage (5.2.1 p184). Surprise gives Disadvantage on the Initiative roll (5.2.1 p13) |
| Proficiency Bonus | +2 to +6 by total level | The same table (5.2.1 p23) |
| Passive Perception | 10 + modifiers, +5 with advantage, -5 with disadvantage (5.1 p78) | The same formula (5.2.1 p22, p186) |
| Carrying capacity | Str × 15. Push, drag, or lift Str × 30. Double per size above Medium, halve for Tiny. Optional encumbrance variant at Str × 5 and Str × 10 (5.1 p79 to p80) | The same values in a table by size (5.2.1 p178). No encumbrance variant in SRD 5.2.1. Goliath Powerful Build counts one size larger (5.2.1 p86) |
| Unarmed Strike | 1 + Str modifier bludgeoning (5.1 p95) | Choose Damage (1 + Str modifier Bludgeoning), Grapple, or Shove (5.2.1 p190) |
| Expertise | A class feature | A glossary rule: double the Proficiency Bonus for one skill. Expertise cannot be gained twice in the same skill (5.2.1 p182) |
| Ability score caps above 20 | Barbarian Primal Champion max 24 (5.1 p9) | Primal Champion and Body and Mind max 25 (5.2.1 p30, p52). Epic Boon feats max 30 (5.2.1 p88) |

---

## C. Official backward-compatibility guidance

### What I verified

SRD 5.2.1 has no general rule for using 2014 content. It has these verified references to older material:

- Blessed Strikes: "if you get either option from a Cleric subclass in an older book, use only the option you choose for this feature" (5.2.1 p38).
- Wild Shape: "When choosing known forms, you may look in other sources for eligible Beasts if the Game Master permits you to do so" (5.2.1 p42).
- Species: "playable non-Humanoid species appear in other books" (5.2.1 p83).
- Rules Glossary: "If you're looking for a term from an earlier version of the fifth edition rules, consult the index" (5.2.1 p176).

### What I could not verify

The official guidance for 2014 races and backgrounds is in the 2024 Player's Handbook and in D&D Beyond articles. I could not read any of them because the proxy blocked `dndbeyond.com` and `roll20.net`, and the 2024 Player's Handbook is not freely available.

**⚠️ UNVALIDATED SPECULATION — the following comes from search-engine summaries of `https://www.dndbeyond.com/posts/1783-the-10-species-in-the-2024-players-handbook`, `https://www.dndbeyond.com/posts/1785-the-backgrounds-and-origin-feats-in-the-2024`, and `https://www.dndbeyond.com/posts/1745-whats-new-in-the-2024-players-handbook`. I could not read the pages or the book, so none of it is quoted text.** The summaries say the 2024 Player's Handbook has a sidebar in its character creation chapter, on page 38, about backgrounds and species from older books. The rules they report are:

1. An older species: ignore its ability score increases. The background supplies them.
2. An older background: increase one ability by 2 and another by 1, or three abilities by 1, chosen by the player. If the background has no feat, take an Origin feat of the player's choice.

The summaries say nothing about the languages of older races or the features of older backgrounds. Get the exact wording from the 2024 Player's Handbook before building on it.

---

## D. Prior art: open-source dual-edition support

### D1. Foundry VTT `dnd5e`

Foundry uses two layers: a world setting that picks the rules to compute with, and a per-document field that records which rules a document was written for.

**World setting.** `rulesVersion` is a world-scoped setting with choices `modern` and `legacy`. The default is `modern`, and changing it requires a reload (`module/settings.mjs:101`):

```js
game.settings.register("dnd5e", "rulesVersion", {
  scope: "world", config: true, default: "modern", type: String,
  choices: { modern: "SETTINGS.DND5E.RULESVERSION.Modern", legacy: "SETTINGS.DND5E.RULESVERSION.Legacy" },
  requiresReload: true
});
```

The labels are "Modern Rules (2024)" and "Legacy Rules (2014)". The hint reads "Change handling of various rules between the 2024 and 2014 rule sets" (`lang/en.json`, keys `SETTINGS.DND5E.RULESVERSION.*`).

**Per-document field.** Every item and actor has `system.source.rules`, a string that defaults from the world setting (`module/data/shared/source-field.mjs:20`):

```js
rules: new StringField({
  initial: () => dnd5e.settings.rulesVersion === "modern" ? "2024" : "2014"
}),
```

The migration marks all data created before 4.0.0 as `"2014"` (`module/migration.mjs:540` for actors, `:624` for items). Text enrichment resolves the version in this order: an explicit config value, the parent document's `source.rules`, the document's own `source.rules`, then the world setting (`getRulesVersion`, `module/enrichers.mjs:138`). Actor sheets use the actor's `source.rules` if it is set and fall back to the world setting (`module/applications/actor/api/base-actor-sheet.mjs:223`).

**Compendium split.** Legacy and modern content live in separate packs. The legacy packs are tagged `sourceBook: "SRD 5.1"` and the modern packs `sourceBook: "SRD 5.2"`. `system.json` groups them into the pack folders "D&D Modern Content" and "D&D Legacy Content":

| Legacy pack (SRD 5.1) | Modern pack (SRD 5.2) |
|---|---|
| `classes`, `subclasses`, `classfeatures` | `classes24` (classes, subclasses, and features together) |
| `races`, `backgrounds` | `origins24` (species, backgrounds, and their traits) |
| No feat pack. The legacy Grappler feat is in `classfeatures` (`packs/_source/classfeatures/grappler.yml`) | `feats24` |
| `spells` | `spells24` |
| `items`, `tradegoods` | `equipment24` |
| `monsters`, `monsterfeatures`, `heroes` | `actors24`, `monsterfeatures24` |
| `rules`, `tables` | `content24`, `tables24` |

The same concept keeps its `identifier` across editions but gets a new document ID. For example, the legacy Acolyte has `_id: IgJkSnLiLJOWH7eK` and the modern Acolyte has `_id: phbbgAcolyte0000`. Both have `identifier: acolyte` and differ in `source.rules` (`packs/_source/backgrounds/acolyte.yml`, `packs/_source/origins24/backgrounds/acolyte.yml`).

**How code branches.** At this commit, 31 lines under `module/` read `dnd5e.settings.rulesVersion`. To recount, run `grep -rn "dnd5e.settings.rulesVersion" module --include=*.mjs | wc -l`. Representative branches:

- New race items get ability score, size, and Common language advancements under legacy rules and only a size advancement under modern rules (`module/data/item/race.mjs:185`).
- New background items get an `AbilityScoreImprovement` with 3 points, proficiencies, a language choice of Common plus two, and a feat grant under modern rules. Under legacy rules they get proficiencies and a feature (`module/data/item/background.mjs:72`).
- The modern Soldier background models the +2/+1 or +1/+1/+1 rule as `points: 3`, `cap: 2`, and `locked: [int, wis, cha]` (`packs/_source/origins24/backgrounds/soldier.yml:88`).
- A language key map converts Draconic from exotic to standard and moves Thieves' Cant and Druidic to exotic, in both directions (`module/data/advancement/trait-data.mjs:11`).
- Class Ability Score Improvement advancements always allow feats under modern rules. They count as Epic Boon at level 19 when the class's `source.rules` is `"2024"` or, if unset, when the setting is modern (`module/documents/advancement/ability-score-improvement.mjs:43`, `:72`, `:83`).
- Initiative: Jack of All Trades applies only under legacy rules. The Alert flag adds the full Proficiency Bonus only under modern rules (`module/data/actor/templates/attributes.mjs:495`).
- The concentration DC is capped at 30 under modern rules (`module/documents/actor/actor.mjs:551`). Hit Dice rolls have a minimum of 1 under modern rules (`:2029`). A long rest recovers all Hit Dice under modern rules and half under legacy rules (`:2536`).

In the advancement code I searched, I found no logic that removes a 2014 race's ability score increases when the world uses modern rules.

**Release history.** `rulesVersion` first appears at tag `release-4.0.0`. It is absent from `module/settings.mjs` at `release-3.3.1`. The 4.0.0 release notes (2024-09-13) say: "By default, the 4.0.0 release uses the latest rules, but you may opt to continue using the legacy rules by adjusting the 'Rules Version' setting" (`https://github.com/foundryvtt/dnd5e/releases/tag/release-4.0.0`). The `*24` SRD 5.2 packs first appear in `system.json` at `release-4.4.0` and are absent at `release-4.3.9`. The 4.4.0 release notes (2025-04-29) say the release "contains many of the same features and improvements as the 5.0 release, including the SRD 5.2" (`https://github.com/foundryvtt/dnd5e/releases/tag/release-4.4.0`).

### D2. 5e-bits: `5e-database` and `5e-srd-api`

**`5e-database`** keeps each edition in its own directory tree: `src/2014/` and `src/2024/`, each with `en/` and translations. The 2024 tree has 2024-only files: `5e-SRD-Species.json`, `5e-SRD-Subspecies.json`, `5e-SRD-Weapon-Mastery-Properties.json`, and `5e-SRD-Poisons.json`. The 2014 tree has `5e-SRD-Races.json` and `5e-SRD-Subraces.json` instead. The loader derives the MongoDB collection name from the year directory, giving names such as `2014-spells` and `2024-spells` (`scripts/dbUtils.ts:66`).

The 2024 data models the lineage, legacy, and ancestry choices as Subspecies. There are 24 entries, for example `draconic-ancestor-red`, `elven-lineage-drow`, `giant-ancestry-stones-endurance`, and `fiendish-legacy-infernal` (`src/2024/en/5e-SRD-Subspecies.json`). The same `index` can exist in both editions. `acolyte` exists at `/api/2014/backgrounds/acolyte` and `/api/2024/backgrounds/acolyte`. A 2024 background holds `ability_scores`, `feat`, `proficiencies`, and `equipment_options` (`src/2024/en/5e-SRD-Backgrounds.json`).

The 2024 data is partial at this commit. `5e-SRD-Monsters.json` has 3 entries. The other 2024 counts are: 339 spells, 17 feats, 12 classes, 12 subclasses, 9 species, 4 backgrounds, 8 mastery properties. The changelog records 2024 work as a series of additions, for example "2024: Add Traits + Species (#1014)" (`CHANGELOG.md`).

**`5e-srd-api`** mounts one router per edition: `router.use('/2014', v2014Handler)` and `router.use('/2024', v2024Handler)` (`apps/api/src/routes/api.ts:10`). It runs two GraphQL schemas at `/graphql/2014` and `/graphql/2024`, and the bare `/graphql` path, marked deprecated, serves 2014 (`apps/api/src/server.ts`). Models are duplicated per edition under `apps/api/src/models/2014/` and `apps/api/src/models/2024/`. Each 2024 model binds to a year-prefixed collection, for example `@srdModelOptions('2024-species')` (`apps/api/src/models/2024/species.ts:12`).

### D3. Open5e (`open5e-api`)

Open5e stores SRD 5.2 as a separate document, `srd-2024`, under a separate game system, `5e-2024`. SRD 5.1 is document `srd-2014` under game system `5e-2014` (`data/v2/GameSystem.json`, `data/v2/wizards-of-the-coast/srd-2024/Document.json`, `data/v2/wizards-of-the-coast/srd-2014/Document.json`). Each content row belongs to one document through a `document` foreign key (`FromDocument` in `api_v2/models/document.py`). Each document belongs to one game system (`Document.gamesystem`, same file). Content keys carry the document key as a prefix, for example `srd-2024_dwarf` and `srd-2024_acolyte`.

The `srd-2024` document record names "System Reference Document 5.2", lists license `cc-by-40`, and has `publication_date` 2024-01-01. The SRD 5.2.1 PDF metadata dates are in April 2025, so treat Open5e's dates as unreliable. All nine 2024 species rows have an empty `subspecies_of`, so Open5e has no separate species rows for lineages, legacies, or ancestries (`data/v2/wizards-of-the-coast/srd-2024/Species.json`).

### D4. Other verified projects

**Charnik** (`FernDragonborn/charnik`) is an open-source desktop character builder and tracker for "D&D 5e (2014) + 5.5e (2024)" (`README.md`). Its approach, verified in source:

- One list of systems: `export const SYSTEMS = ['5e', '5.5e'] as const;` (`src/lib/rules/pipeline.ts:13`).
- Shared rules functions take a `system` argument where the rules differ. For example, `hitDiceRecoveredOnLongRest` returns all dice for `5.5e` and half for `5e` (`src/lib/rules/core.ts:222`). `carryingCapacity` adds encumbrance notes only for `5e` (`src/lib/rules/core.ts:311`).
- A character is bound to the system it was created in. Conversion between systems is out of scope: "you don't reinterpret a 5e character as 5.5e" (`docs/plan.md:200` to `:204`).
- Content ships as one pack per edition, `srd-2014/` and `srd-2024/`. Each CSV declares its system in a header such as `#content-systems: 5.5e` (`FernDragonborn/charnik-content-srd`, `README.md` and `srd-2024/species_srd.csv`).

**`hewnhero-srd`** (`CoolFireGiant/hewnhero-srd`) is a data pack in the 5etools JSON schema for the HewnHero app. I did not read the app's source. It tags each entry with a source code, `SRD51` or `SRD52`. 2024 entries also carry `edition: "one"`. A 2014 entry with a 2024 counterpart links to it with `reprintedAs`, for example the SRD51 Dwarf has `reprintedAs: ["Dwarf|SRD52"]` (`data/races.json`).

---

## Open questions

1. The exact text of the 2024 Player's Handbook sidebar on species and backgrounds from older books, including how it treats a 2014 race's languages and a 2014 background's feature. Blocked: `dndbeyond.com` and `roll20.net` were unreachable, and the book is not free.
2. The release dates of SRD 5.2 and SRD 5.2.1 from a Wizards page. Blocked: `dndbeyond.com`.
3. The full list of differences between SRD 5.2 and SRD 5.2.1. The SRD 5.2 PDF was not obtained.
4. Whether any SRD version later than 5.2.1 exists. Searches found none.
5. The SRD 5.1 OGL edition's attribution text and Section 15 notice. The OGL PDF was not obtained.
