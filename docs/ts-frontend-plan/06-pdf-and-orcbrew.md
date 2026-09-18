# Phase 5 — PDF Export and `.orcbrew` Homebrew Import

Two features that look scary but mostly come free, because the heavy lifting
is server-side or already in the engine.

## PDF export

How it works today:

1. The client computes a flat map of PDF form-field values from the built
   character — `src/cljc/orcpub/pdf_spec.cljc` (`make-spec` and friends).
   Field names match the fillable AcroForm PDFs in `resources/`
   (`fillable-char-sheetstyle-<style>-<spell-pages>-spells.pdf`).
2. It POSTs the field map to `POST /character.pdf` (handler `character-pdf-2`
   in `routes.clj`; filling logic in `src/clj/orcpub/pdf.clj` using Apache
   PDFBox), which streams back the filled PDF.

Plan:

- `pdf_spec.cljc` is `.cljc`, so **expose it through the engine facade**:
  `pdfFieldValues(entity): Record<string, string | boolean>` — zero logic to
  port.
- Check the exact request encoding of `POST /character.pdf` in the Phase 0
  fixture (it is a form post from the old client, not a JSON API call) and
  replicate it; trigger the download with a hidden form or fetch + blob.
- Sheet-style selection (the 4 styles × spell-page variants) is a client-side
  choice of which template the server fills; mirror the old UI's picker.

Effort: small. Do it immediately after the read-only sheet (4.2) — it's a
high-value demo and validates `pdfFieldValues` against the golden PDFs from
Phase 0.

## `.orcbrew` homebrew import

`.orcbrew` files are **EDN** (Clojure's data literal syntax) containing
homebrew content: classes, races, spells, monsters, etc. The current app
parses and validates them client-side (`import_validation.cljs`, 67KB —
validation rules documented in `docs/ORCBREW_FILE_VALIDATION.md` and
`docs/HOMEBREW_REQUIRED_FIELDS.md`), merges them into the template, and keeps
them in localStorage. There is also conflict-resolution UI for duplicate
content (`docs/CONFLICT_RESOLUTION.md`, `views/conflict_resolution.cljs`).

Plan, in increasing ambition:

1. **Parse + apply (MVP)**: facade exposes `parseOrcbrew(text)` (EDN parsing
   via `cljs.reader` inside the engine — no JS EDN library needed) and
   `withHomebrew(content)` to extend the template. Persist raw file text in
   localStorage; re-apply on load. Characters using homebrew now build
   correctly — verify with the Phase 0 homebrew golden character.
2. **Validation**: expose the existing validation as
   `validateOrcbrew(content): Issue[]` if `import_validation.cljs` functions
   can be lifted into the facade (they are cljs, same compilation unit — check
   for UI coupling), otherwise reimplement the required-fields checks in TS
   from `docs/HOMEBREW_REQUIRED_FIELDS.md` and show an import log like
   `views/import_log.cljs`.
3. **Conflict resolution**: port the duplicate-detection/merge UX per
   `docs/CONFLICT_RESOLUTION.md`. This is UI work over engine-provided
   diffing; defer until real users hit it.

Export: homebrew content back out as `.orcbrew` = EDN serialization; expose
`toOrcbrew(content): string` from the facade.

## Exit criteria

- [ ] PDF export byte-comparable (or visually identical) to reference PDFs
      for the golden characters
- [ ] A known-good `.orcbrew` file (test fixtures exist in `test/`, e.g.
      `duplicate-external-a.orcbrew`) imports, extends the builder's options,
      and its golden character builds correctly
- [ ] Import of a malformed file fails with readable errors, not a crash
