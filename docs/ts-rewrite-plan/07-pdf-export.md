# 07 — PDF Export

Filling the same 28 fillable character sheets the old app uses, with the
same field names, so printed output is identical. Source of truth:
`src/cljc/orcpub/pdf_spec.cljc` (field map), `src/clj/orcpub/pdf.clj`
(filling, images, spell cards), `routes.clj:626-681` (template selection,
flattening, images).

## The templates

`resources/fillable-char-sheetstyle-<style>-<N>-spells.pdf`, `style` ∈ 1..4,
`N` ∈ 0..6 spell pages — 28 files. Copy them into the new app's assets
(check their license/provenance first: they are community-made fillable
versions of the WotC sheet; the old repo ships them, and the new one can
carry the same files under the same terms). Field names are the AcroForm
names; the old code writes `(name k)` of each spec key verbatim
(`pdf.clj:87-108`), so the spec key **is** the field name.

## The field map

Reproduce `make-spec` (`pdf_spec.cljc:571-673`) as
`pdfFields(built, options): Record<string, string | boolean>`. Field
catalogue (from the output-surface report §3):

- **Identity**: `character-name`, `character-name-2`, `player-name`, `race`
  (`"Race/Subrace"`), `alignment`, `class-level` (`"Fighter (5) / Wizard
  [Evocation] (3)"`), `background`, `xp`, `faction-name`, `age`, `height`,
  `weight`, `eyes`, `skin`, `hair`, `backstory`, `personality-traits` (both
  traits joined by a blank line), `ideals`, `bonds`, `flaws`.
- **Combat**: `prof-bonus`, `ac`, `hd` (multi-line `"2x(1d10+3)"` grouped by
  die), `initiative`, `speed` (`"40/30"` when unarmored differs), `hp-max`,
  `hp-current`, `passive`.
- **Abilities**: `str`…`cha` and `str-mod`…`cha-mod`. **Quirk to preserve**:
  which box gets the score vs the modifier is swapped by the
  `print-large-abilities?` option (`pdf_spec.cljc:588-596`) — default puts
  the *modifier* in the big box.
- **Saves**: `str-save`…`cha-save` (bonus string) and `*-save-check`
  (proficiency booleans).
- **Skills**: 18 × `<skill>` + `<skill>-check` (`acrobatics`,
  `animal-handling`, … `sleight-of-hand`, `survival`).
- **Weapons**: `weapon-name-1..3`, `weapon-attack-bonus-1..3`,
  `weapon-damage-1..3` (first three equipped non-ammunition weapons;
  versatile weapons add a `"(two-handed)"` row); `attacks-and-spellcasting`
  free text = `"Number of Attacks: N"` + structured attacks + weapons 4..n.
- **Proficiencies**: `other-profs` — four `;`-joined paragraphs (tools,
  weapons, armor, languages).
- **Features**: `features-and-traits-2` — header lines (darkvision, crit
  range, resistances/immunities) then the four dashed-banner sections
  (`----------Bonus Actions----------` etc.) or a flat trait list; entries
  `"Name. Description"`, sorted case-insensitively.
- **Equipment**: `cp sp ep gp pp`; **`features-and-traits`** (yes, that
  field) holds the equipped-item list `"Name x3"`; `treasure` holds
  non-coin treasure plus unequipped items `"Name (3)"`.
- **Spell pages** (per page N): `spellcasting-class-N`,
  `spellcasting-ability-N`, `spell-save-dc-N`, `spell-attack-bonus-N`,
  `spell-slots-L-N`, `spells-L-I-N`.

Page assembly (`pdf_spec.cljc:252-335`): group spells by **spellcasting
ability** (not class); per level, partition by the row capacity table
`{0: 8, 1: 12, 2: 13, 3: 13, 4: 13, 5: 9, 6: 9, 7: 9, 8: 7, 9: 7}`; pages
per ability = max partitions; sort spells by name; optional prepared-only
filter for prepare-casters. Template `N` = highest `spellcasting-class-K`
present, 6 → 0.

Checkbox values are `"Yes"`/`"Off"`. Unknown fields are skipped. Text field
font sizes for the non-Chrome flattening path are in `routes.clj:206-222`.

## Two implementations, same field map

**A. Server-side via the adapter (first).** When running against an old
server, `POST /character.pdf` with the field map — the old endpoint fills,
flattens, adds images and spell cards. Zero PDF code in the new app;
proves the field map is right (the old server does exactly what it does
for the old client).

**B. Browser-side (for local-first and the new backend).** Use `pdf-lib`
(pure JS, fills AcroForms, embeds images, draws text) to:
1. Load the chosen template, set each field, flatten optionally.
2. Draw portrait / faction images at the per-style coordinates
   (`routes.clj:664-681` — inches from top-left, style 4 puts the portrait
   on page 0). Fetch through a CORS-safe path; skip silently on failure as
   the old code does.
3. Spell cards (optional feature): 2.5″ × 3.5″, 3 × 3 per page, drawn
   right-to-left per row, with backs for overflow (`pdf.clj:396-586`). Fonts
   are the Vollkorn TTFs in `resources/` (OFL-licensed; embeddable). Port
   last — it's the least-used feature and the most drawing code.

## Tests

- Field-map golden tests: for each golden character, the old
  `make-spec` output (dumped from a REPL, same script as doc 04) equals
  `pdfFields(built)` key-for-key.
- Rendering smoke test: fill each of the 28 templates with a fixture and
  read the fields back with `pdf-lib`; compare to expected.
- Visual diff (manual, once per sheet style) against the old server's
  output for the same character.

## Deliverables

- [ ] `pdfFields()` with golden tests against old `make-spec` dumps
- [ ] Adapter path using `POST /character.pdf`
- [ ] `pdf-lib` filler with images; flatten option
- [ ] Spell cards (later)
