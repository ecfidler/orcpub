import { readFileSync } from "node:fs";
import { describe, expect, it } from "vitest";
import { parseOrcbrew, reconcileMissingContent } from "@pubdoor/dmv";
import type { MissingContent, UnresolvedOption } from "@pubdoor/dmv";

const fixtures = new URL("../../fixtures/", import.meta.url);

function strict(path: string): object {
  return JSON.parse(readFileSync(new URL(`${path}.strict.json`, fixtures), "utf8"));
}

function pack(name: string) {
  const parsed = parseOrcbrew(readFileSync(new URL(`orcbrew/${name}.orcbrew`, fixtures), "utf8"), { name });
  expect(parsed.success).toBe(true);
  return parsed.data!;
}

function byKey<T extends { key: string }>(items: T[]): T[] {
  return [...items].sort((a, b) => a.key.localeCompare(b.key));
}

/** Each item without its suggestions, sorted by key. */
function reported(items: MissingContent[]) {
  return byKey(items).map(({ key, contentType, label, path }) => ({ key, contentType, label, path }));
}

describe("reconcileMissingContent: r8-unresolved-keys", () => {
  const entity = strict("legacy/r8-unresolved-keys");

  it("reports the race, subrace, class and subclass without their pack", () => {
    const report = reconcileMissingContent(entity);

    expect(report.hasMissing).toBe(true);
    expect(reported(report.items)).toStrictEqual([
      {
        key: "alchemist",
        contentType: "subclass",
        label: "Subclass",
        path: ["class", "artificer", "levels", "level-3", "artificer-specialization", "alchemist"],
      },
      { key: "artificer", contentType: "class", label: "Class", path: ["class", "artificer"] },
      { key: "envoy", contentType: "subrace", label: "Subrace", path: ["race", "ironwrought", "subrace", "envoy"] },
      { key: "ironwrought", contentType: "race", label: "Race", path: ["race", "ironwrought"] },
    ]);
    // Nothing loaded, so nothing to suggest.
    for (const item of report.items) expect(item.suggestions).toStrictEqual([]);
    // The class's levels and hit points are unresolved too, but their class
    // is reported, so they are not listed again.
    expect(report.unresolvedOptions).toStrictEqual([]);
  });

  it("reports nothing with duplicate-external-b loaded", () => {
    expect(reconcileMissingContent(entity, pack("duplicate-external-b"))).toStrictEqual({
      hasMissing: false,
      items: [],
      unresolvedOptions: [],
    });
  });

  it("reports only what another pack leaves unresolved", () => {
    // duplicate-external-a has an :artificer class but no Ironwrought race
    // or Alchemist subclass.
    const report = reconcileMissingContent(entity, pack("duplicate-external-a"));

    expect(reported(report.items).map(({ key, contentType }) => [key, contentType])).toStrictEqual([
      ["alchemist", "subclass"],
      ["envoy", "subrace"],
      ["ironwrought", "race"],
    ]);
  });

  it("suggests renamed content with the old scores", () => {
    const renamed = parseOrcbrew(
      `{:orcpub.dnd.e5/races {:ironwrought-v2 {:key :ironwrought-v2 :name "Ironwrought" :option-pack "Renamed"}}
        :orcpub.dnd.e5/classes {:artificer-v2 {:key :artificer-v2 :name "Artificer" :option-pack "Renamed" :hit-die 8}}}`,
      { name: "Renamed" },
    );
    expect(renamed.success).toBe(true);

    const items = byKey(reconcileMissingContent(entity, renamed.data!).items);
    const suggestions = Object.fromEntries(items.map((item) => [item.key, item.suggestions]));

    // Same base before the first dash: 0.8 (content_reconciliation.cljs).
    expect(suggestions["ironwrought"]).toStrictEqual([
      { key: "ironwrought-v2", name: "Ironwrought", source: "Renamed", similarity: 0.8 },
    ]);
    expect(suggestions["artificer"]).toStrictEqual([
      { key: "artificer-v2", name: "Artificer (Renamed)", source: "Renamed", similarity: 0.8 },
    ]);
    expect(suggestions["envoy"]).toStrictEqual([]);
    expect(items.map((item) => item.inferredSource)).toStrictEqual([null, null, null, null]);
  });

  it("accepts the entity and homebrew as text", () => {
    const homebrew = pack("duplicate-external-b");

    expect(reconcileMissingContent(JSON.stringify(entity))).toStrictEqual(reconcileMissingContent(entity));
    expect(reconcileMissingContent(entity, JSON.stringify(homebrew))).toStrictEqual(
      reconcileMissingContent(entity, homebrew),
    );
  });
});

describe("reconcileMissingContent: character-test-2", () => {
  const entity = strict("legacy/character-test-2");

  it("reports Eldritch Knight, Noble and Ritual Caster with no homebrew loaded", () => {
    const report = reconcileMissingContent(entity);

    expect(report.hasMissing).toBe(true);
    expect(reported(report.items)).toStrictEqual([
      {
        key: "eldritch-knight",
        contentType: "subclass",
        label: "Subclass",
        path: ["class", "fighter", "levels", "level-3", "martial-archetype", "eldritch-knight"],
      },
      { key: "noble", contentType: "background", label: "Background", path: ["background", "noble"] },
      { key: "ritual-caster", contentType: "feat", label: "Feat", path: ["feats", "ritual-caster"] },
    ]);
  });

  it("lists the other unresolved choices whose parent resolves", () => {
    // A magic armor key without its damage type, and a top-level
    // skill-profs selection the template no longer has. The spells under
    // Eldritch Knight are covered by its item.
    const options: UnresolvedOption[] = reconcileMissingContent(entity).unresolvedOptions;

    expect(byKey(options)).toStrictEqual([
      { key: "animal-handling", path: ["skill-profs", "animal-handling"] },
      { key: "armor-of-resistance-half-plate", path: ["magic-armor", "armor-of-resistance-half-plate"] },
      { key: "intimidation", path: ["skill-profs", "intimidation"] },
    ]);
  });
});

describe("reconcileMissingContent: resolved characters", () => {
  it("reports nothing for an SRD character", () => {
    expect(reconcileMissingContent(strict("characters/fighter-3-wizard-2"))).toStrictEqual({
      hasMissing: false,
      items: [],
      unresolvedOptions: [],
    });
  });

  it("reports nothing for a homebrew character with its pack", () => {
    const report = reconcileMissingContent(strict("characters/ironwrought-artificer-3"), pack("duplicate-external-b"));

    expect(report.hasMissing).toBe(false);
  });
});
