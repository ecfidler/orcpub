# Phase 5: PDF export and `.orcbrew` homebrew import

These two features look large but need little new code, because the hard
parts are server-side or already in the engine.

## PDF export

How it works today:

1. The client computes a flat map of PDF form-field values from the built
   character, in `src/cljc/orcpub/pdf_spec.cljc` (`make-spec` and its
   helpers). The field names match the fillable AcroForm PDFs in
   `resources/` (`fillable-char-sheetstyle-<style>-<spell-pages>-spells.pdf`).
2. It posts the field map to `POST /character.pdf`. The handler is
   `character-pdf-2` in `routes.clj`, and the filling logic is in
   `src/clj/orcpub/pdf.clj`, using Apache PDFBox. The server streams back
   the filled PDF.

Plan:

- `pdf_spec.cljc` is `.cljc`, so expose it through the engine facade as
  `pdfFieldValues(entity): Record<string, string | boolean>`. There is no
  logic to port.
- Check the exact request encoding of `POST /character.pdf` in the Phase 0
  fixture. It is a form post from the old client, not a JSON API call.
  Replicate it, and trigger the download with a hidden form or with fetch
  and a blob.
- Sheet-style selection (the 4 styles times the spell-page variants) is a
  client-side choice of which template the server fills. Mirror the old
  UI's picker.

The effort is small. Do it immediately after the read-only sheet (4.2). It
is a high-value demo, and it validates `pdfFieldValues` against the golden
PDFs from Phase 0.

## `.orcbrew` homebrew import

`.orcbrew` files are EDN, Clojure's data literal syntax, and contain
homebrew content such as classes, races, spells, and monsters. The current
app parses and validates them client-side in `import_validation.cljs`
(67 KB). `docs/ORCBREW_FILE_VALIDATION.md` and
`docs/HOMEBREW_REQUIRED_FIELDS.md` document the validation rules. The app
then merges the content into the template and keeps it in localStorage.
There is also a conflict-resolution UI for duplicate content
(`docs/CONFLICT_RESOLUTION.md`, `views/conflict_resolution.cljs`).

The plan, in increasing ambition:

1. **Parse and apply, the minimum.** The facade exposes
   `parseOrcbrew(text)`, which parses EDN with `cljs.reader` inside the
   engine, so no JS EDN library is needed, and `withHomebrew(content)` to
   extend the template. Persist the raw file text in localStorage and
   re-apply it on load. Characters that use homebrew now build correctly.
   Verify this with the Phase 0 homebrew golden character.
2. **Validation.** Expose the existing validation as
   `validateOrcbrew(content): Issue[]` if the `import_validation.cljs`
   functions can be lifted into the facade. They are ClojureScript in the
   same compilation unit, so check for UI coupling. Otherwise, reimplement
   the required-fields checks in TypeScript from
   `docs/HOMEBREW_REQUIRED_FIELDS.md` and show an import log like
   `views/import_log.cljs`.
3. **Conflict resolution.** Port the duplicate detection and merge UX
   described in `docs/CONFLICT_RESOLUTION.md`. This is UI work over
   engine-provided diffing. Defer it until real users hit the problem.

Export writes homebrew content back out as `.orcbrew`, which is EDN
serialization. Expose `toOrcbrew(content): string` from the facade.

## Exit criteria

- [ ] PDF export is byte-comparable, or visually identical, to the reference PDFs for the golden characters
- [ ] A known-good `.orcbrew` file, for example `test/duplicate-external-a.orcbrew`, imports, extends the builder's options, and builds its golden character correctly
- [ ] Import of a malformed file fails with readable errors, not a crash
