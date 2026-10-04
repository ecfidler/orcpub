import { readFileSync } from "node:fs";
import { gunzipSync } from "node:zlib";
import type { TemplateSelection } from "@pubdoor/dmv";

export interface KeySets {
  selectionKeys: string[];
  optionKeys: string[];
}

/** The distinct selection and option keys of a template shape, sorted. */
export function keySets(shape: TemplateSelection[]): KeySets {
  const selectionKeys = new Set<string>();
  const optionKeys = new Set<string>();
  const walk = (selection: TemplateSelection): void => {
    selectionKeys.add(selection.key);
    for (const option of selection.options) {
      optionKeys.add(option.key);
      option.selections?.forEach(walk);
    }
  };
  shape.forEach(walk);
  return { selectionKeys: [...selectionKeys].sort(), optionKeys: [...optionKeys].sort() };
}

/**
 * The C3 content-identity baseline: every built-in selection and option key
 * of the JVM oracle's SRD template (fixtures/orcbrew/_srd-baseline.template.json.gz).
 */
export function baselineKeySets(): KeySets {
  const baseline = new URL("../../fixtures/orcbrew/_srd-baseline.template.json.gz", import.meta.url);
  const { shape } = JSON.parse(gunzipSync(readFileSync(baseline)).toString("utf8")) as {
    shape: TemplateSelection[];
  };
  return keySets(shape);
}
