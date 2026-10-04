// Writes orcbrewToEdn's exports of every fixtures/orcbrew pack, for the
// old-spec acceptance check (scripts/check-orcbrew-exports.clj).
//
// Usage, after npm run build:
//
//   node scripts/write-orcbrew-exports.mjs <out-dir>
//
// For each fixture <f>, <out-dir> gets:
//   <f>.orcbrew            all packs, the multi-plugin map
//   <f>.pretty.orcbrew     the same, pretty-printed
//   <f>.roundtrip.orcbrew  the same after parseOrcbrew reads the export again
//   single/<f>--<n>.orcbrew  each pack alone, the single-plugin map
import { mkdirSync, readdirSync, readFileSync, writeFileSync } from "node:fs";
import { join } from "node:path";
import { orcbrewToEdn, parseOrcbrew } from "../dist/pubdoor.js";

const out = process.argv[2];
if (!out) {
  console.error("usage: node scripts/write-orcbrew-exports.mjs <out-dir>");
  process.exit(2);
}
mkdirSync(join(out, "single"), { recursive: true });

const fixtures = new URL("../../fixtures/orcbrew/", import.meta.url);
for (const file of readdirSync(fixtures).filter((f) => f.endsWith(".orcbrew")).sort()) {
  const name = file.slice(0, -".orcbrew".length);
  const { success, data } = parseOrcbrew(readFileSync(new URL(file, fixtures), "utf8"), { name });
  if (!success) throw new Error(`${file} does not import`);

  const text = orcbrewToEdn(data);
  writeFileSync(join(out, `${name}.orcbrew`), text);
  writeFileSync(join(out, `${name}.pretty.orcbrew`), orcbrewToEdn(data, { pretty: true }));
  writeFileSync(join(out, `${name}.roundtrip.orcbrew`), orcbrewToEdn(parseOrcbrew(text).data));
  Object.keys(data).forEach((pack, n) => {
    writeFileSync(join(out, "single", `${name}--${n}.orcbrew`), orcbrewToEdn(data, { pack }));
  });
}
