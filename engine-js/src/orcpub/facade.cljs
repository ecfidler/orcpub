(ns orcpub.facade
  "The exported API of @dmv/pubdoor. See docs/ts-rewrite-plan/02-engine-library.md.

  Nothing lazy crosses the boundary: every function takes and returns plain
  JS data. The conversion rules are the ones fixtures/README.md documents for
  expected.json (orcpub.oracle/->plain in scripts/orcpub/oracle.clj)."
  (:require [cognitect.transit :as transit]
            [goog.object :as gobj]
            [re-frame.db]
            [orcpub.entity :as entity]
            [orcpub.template :as t]
            [orcpub.dnd.e5.character :as char5e]
            [orcpub.facade.template :as template]))

;;; ---------------------------------------------------------------------------
;;; Plain-data conversion (orcpub.oracle/->plain)
;;; ---------------------------------------------------------------------------

(defn- kw->str [k]
  (if (namespace k) (str (namespace k) "/" (name k)) (name k)))

(defn- simple-key->str [k]
  (cond (keyword? k) (kw->str k)
        (string? k) k
        (number? k) (str k)
        (boolean? k) (str k)
        (nil? k) "nil"
        :else ::composite))

(declare ->plain)

(defn- sort-plain [coll]
  (try (vec (sort coll))
       (catch :default _ (vec (sort-by pr-str coll)))))

(defn- plain-map [m]
  (if (some #{::composite} (map simple-key->str (keys m)))
    ;; For example spells-known, keyed by [class-name spell-key].
    {"__entries" (->> m
                      (map (fn [[k v]] [(->plain k) (->plain v)]))
                      (sort-by (comp pr-str first))
                      vec)}
    (into {} (map (fn [[k v]] [(simple-key->str k) (->plain v)])) m)))

(defn- ->plain
  "keyword → \"ns/name\", set → sorted vector, map → string-keyed map
  (composite keys → {\"__entries\" [[k v] ...]}), fn → \"#fn\", anything
  else unknown → pr-str. ClojureScript has no ratios."
  [x]
  (cond (nil? x) nil
        (string? x) x
        (boolean? x) x
        (keyword? x) (kw->str x)
        (symbol? x) (str x)
        (number? x) x
        (map? x) (plain-map x)
        (set? x) (sort-plain (map ->plain x))
        (sequential? x) (mapv ->plain x)
        (fn? x) "#fn"
        :else (pr-str x)))

;;; ---------------------------------------------------------------------------
;;; Built-character values (character-subs, subs.cljs:628-736)
;;; ---------------------------------------------------------------------------

(def ^:private plain-accessors
  "[json-key accessor]. The key is the ::char5e sub name from character-subs."
  [["base-swimming-speed" char5e/base-swimming-speed]
   ["base-flying-speed" char5e/base-flying-speed]
   ["base-land-speed" char5e/base-land-speed]
   ["speed-with-armor" char5e/land-speed-with-armor]
   ["unarmored-speed-bonus" char5e/unarmored-speed-bonus]
   ["max-hit-points" char5e/max-hit-points]
   ["current-hit-points" char5e/current-hit-points]
   ["hit-point-level-bonus" char5e/hit-point-level-bonus]
   ["class-hit-point-level-bonus" char5e/class-hit-point-level-bonus]
   ["initiative" char5e/initiative]
   ["passive-perception" char5e/passive-perception]
   ["character-name" char5e/character-name]
   ["player-name" char5e/player-name]
   ["faction-name" char5e/faction-name]
   ["proficiency-bonus" char5e/proficiency-bonus]
   ["save-bonuses" char5e/save-bonuses]
   ["saving-throws" char5e/saving-throws]
   ["race" char5e/race]
   ["subrace" char5e/subrace]
   ["age" char5e/age]
   ["sex" char5e/sex]
   ["height" char5e/height]
   ["weight" char5e/weight]
   ["hair" char5e/hair]
   ["eyes" char5e/eyes]
   ["skin" char5e/skin]
   ["alignment" char5e/alignment]
   ["background" char5e/background]
   ["classes" char5e/classes]
   ["levels" char5e/levels]
   ["darkvision" char5e/darkvision]
   ["skill-profs" char5e/skill-proficiencies]
   ["skill-bonuses" char5e/skill-bonuses]
   ["skill-expertise" char5e/skill-expertise]
   ["tool-profs" char5e/tool-proficiencies]
   ["tool-expertise" char5e/tool-expertise]
   ["weapon-profs" char5e/weapon-proficiencies]
   ["armor-profs" char5e/armor-proficiencies]
   ["resistances" char5e/damage-resistances]
   ["damage-vulnerabilities" char5e/damage-vulnerabilities]
   ["damage-immunities" char5e/damage-immunities]
   ["immunities" char5e/immunities]
   ["condition-immunities" char5e/condition-immunities]
   ["languages" char5e/languages]
   ["abilities" char5e/ability-values]
   ["race-ability-increases" char5e/race-ability-increases]
   ["subrace-ability-increases" char5e/subrace-ability-increases]
   ["ability-increases" char5e/ability-increases]
   ["ability-bonuses" char5e/ability-bonuses]
   ["armor-class" char5e/base-armor-class]
   ["armor" char5e/normal-armor-inventory]
   ["magic-armor" char5e/magic-armor-inventory]
   ["all-armor-inventory" char5e/all-armor-inventory]
   ["spells-known" char5e/spells-known]
   ["spells-known-modes" char5e/spells-known-modes]
   ["spell-slots" char5e/spell-slots]
   ["pact-magic?" char5e/pact-magic?]
   ["prepares-spells" char5e/prepares-spells]
   ["spell-modifiers" char5e/spell-modifiers]
   ["spell-slot-factors" char5e/spell-slot-factors]
   ["total-spellcaster-levels" char5e/total-spellcaster-levels]
   ["weapons" char5e/normal-weapons-inventory]
   ["magic-weapons" char5e/magic-weapons-inventory]
   ["equipment" char5e/normal-equipment-inventory]
   ["custom-equipment" char5e/custom-equipment]
   ["treasure" char5e/treasure]
   ["custom-treasure" char5e/custom-treasure]
   ["magic-items" char5e/magical-equipment-inventory]
   ["traits" char5e/traits]
   ["attacks" char5e/attacks]
   ["bonus-actions" char5e/bonus-actions]
   ["reactions" char5e/reactions]
   ["actions" char5e/actions]
   ["image-url" char5e/image-url]
   ["image-url-failed" char5e/image-url-failed]
   ["faction-image-url" char5e/faction-image-url]
   ["faction-image-url-failed" char5e/faction-image-url-failed]
   ["personality-trait-1" char5e/personality-trait-1]
   ["personality-trait-2" char5e/personality-trait-2]
   ["xps" char5e/xps]
   ["ideals" char5e/ideals]
   ["bonds" char5e/bonds]
   ["flaws" char5e/flaws]
   ["description" char5e/description]
   ["notes" char5e/notes]
   ["critical-hit-values" char5e/critical-hit-values]
   ["crit-values-str" char5e/crit-values-str]
   ["number-of-attacks" char5e/number-of-attacks]
   ["total-levels" char5e/total-levels]
   ["option-sources" char5e/option-sources]
   ["public?" char5e/public?]
   ["used-resources" char5e/used-resources]
   ["al-illegal-reasons" char5e/al-illegal-reasons]
   ["feats" char5e/feats]
   ["features-used" char5e/features-used]
   ["main-hand-weapon" char5e/main-hand-weapon]
   ["off-hand-weapon" char5e/off-hand-weapon]
   ["worn-armor" char5e/worn-armor]
   ["wielded-shield" char5e/wielded-shield]
   ["attuned-magic-items" char5e/attuned-magic-items]])

(defn- carried-weapons
  "[[weapon-key weapon-map] ...] for every carried weapon, resolved through
  ::mi5e/all-weapons-map."
  [built all-weapons-map]
  (->> (merge (char5e/normal-weapons-inventory built)
              (char5e/magic-weapons-inventory built))
       keys
       sort
       (map (fn [k] [k (get all-weapons-map k)]))))

(defn- armor-combos
  "Every carried armor × carried shield, each side also nil, as
  armor-calculations in subs.cljs does for ::char5e/best-armor-combo."
  [built all-armor-map]
  (let [ac-fn (char5e/armor-class-with-armor built)
        items (map (fn [k] [k (get all-armor-map k)])
                   (sort (keys (char5e/all-armor-inventory built))))
        shield? #(= :shield (:type (second %)))
        shields (filter shield? items)
        armor (remove shield? items)]
    (vec
     (for [[armor-key armor-item] (conj (vec armor) [nil nil])
           [shield-key shield-item] (conj (vec shields) [nil nil])]
       {"armor" (some-> armor-key kw->str)
        "shield" (some-> shield-key kw->str)
        "ac" (ac-fn armor-item shield-item)}))))

(defn- weapon-table [built all-weapons-map]
  (let [attack (char5e/weapon-attack-modifier-fn built)
        damage (char5e/weapon-damage-modifier-fn built)
        best-attack (char5e/best-weapon-attack-modifier-fn built)
        best-damage (char5e/best-weapon-damage-modifier-fn built)
        dual-wield? (char5e/dual-wield-weapon-fn built)
        has-prof? (char5e/has-weapon-prof built)]
    (into {}
          (for [[k weapon] (carried-weapons built all-weapons-map)]
            [(kw->str k)
             (when weapon
               {"attack" {"standard" (attack weapon false)
                          "finesse" (attack weapon true)}
                "best-attack" (best-attack weapon)
                "damage" {"standard" (damage weapon false)
                          "finesse" (damage weapon true)
                          "off-hand" (damage weapon false true)}
                "best-damage" (best-damage weapon)
                "best-damage-off-hand" (best-damage weapon true)
                "dual-wield?" (boolean (dual-wield? weapon))
                "has-prof?" (boolean (has-prof? weapon))})]))))

(defn- keyed-table [ks f]
  (into {} (map (fn [k] [(if (keyword? k) (kw->str k) (str k)) (f k)])) ks))

(defn- built-values
  "Every accessor in plain-accessors, plus the function-valued attributes
  evaluated against the fixed arguments in fixtures/README.md."
  [built {:keys [all-weapons-map all-armor-map]}]
  (let [levels (char5e/levels built)
        tool-profs (char5e/tool-proficiencies built)
        prepares (char5e/prepares-spells built)]
    (-> (into {} (map (fn [[k f]] [k (->plain (f built))])) plain-accessors)
        (assoc "armor-class-with-armor" (armor-combos built all-armor-map)
               "weapon-modifiers" (weapon-table built all-weapons-map)
               "spell-save-dc" (keyed-table char5e/ability-keys
                                            (char5e/spell-save-dc-fn built))
               "spell-attack-modifier" (keyed-table char5e/ability-keys
                                                    (char5e/spell-attack-modifier-fn built))
               "class-level" (keyed-table (sort (keys levels))
                                          (char5e/class-level-fn built))
               "tool-bonus" (keyed-table (sort (if (map? tool-profs) (keys tool-profs) (seq tool-profs)))
                                         (char5e/tool-bonus-fn built))
               "prepare-spell-count" (keyed-table (sort (keys prepares))
                                                  (char5e/prepare-spell-count-fn built))))))

;;; ---------------------------------------------------------------------------
;;; Available selections
;;; ---------------------------------------------------------------------------

(defn- selected-option-keys [template raw selection]
  (let [selected (entity/get-option template raw (entity/actual-path selection))]
    (cond (sequential? selected) (mapv ::entity/key selected)
          (map? selected) (if-let [k (::entity/key selected)] [k] [])
          :else [])))

(defn- selections
  "entity/available-selections flattened to plain data, in the order the
  engine returns them."
  [raw built template]
  ;; The none-option prerequisite reads (:character @app-db)
  ;; (options.cljc:2077). Seed it until patch D2 (ORC-22) reads the entity.
  (swap! re-frame.db/app-db assoc :character raw)
  (mapv (fn [{:keys [::t/key ::t/name ::t/min ::t/max ::t/ref ::t/options
                     ::t/multiselect? ::t/sequential? ::t/require-value?
                     ::entity/path] :as s}]
          (cond-> {"key" (kw->str key)
                   "name" name
                   "path" (->plain path)
                   "actualPath" (->plain (entity/actual-path s))
                   "min" min
                   "max" max
                   "remaining" (entity/count-remaining template raw s)
                   "optionCount" (count options)
                   "selected" (->plain (selected-option-keys template raw s))}
            ref (assoc "ref" (->plain ref))
            multiselect? (assoc "multiselect" true)
            sequential? (assoc "sequential" true)
            require-value? (assoc "requireValue" true)))
        (entity/available-selections raw built template)))

;;; ---------------------------------------------------------------------------
;;; evaluate
;;; ---------------------------------------------------------------------------

(def ^:private srd-template
  "The SRD-only template. homebrew is ignored until buildTemplate (ORC-27)."
  (delay (template/build {})))

(defn- read-strict
  "A strict entity from verbose or normal Transit-JSON text."
  [text]
  (transit/read (transit/reader :json) text))

(defn- evaluate* [text]
  (let [{:keys [template] :as content} @srd-template
        raw (char5e/from-strict (read-strict text))
        built (entity/build raw template)]
    (clj->js {"built" (built-values built content)
              "selections" (selections raw built template)})))

(def ^:private supported-rules #{"2014"})

(def ^:private memo (atom {:key nil :value nil}))

(defn ^:export evaluate
  "Builds a strict entity and returns {built, selections} as plain JS.

  entity is the strict entity as Transit-JSON: the text, or the value
  JSON.parse returns for it. options is {rules?, homebrew?}. rules defaults
  to \"2014\", the only edition 0.1 supports. homebrew is accepted and
  ignored until buildTemplate (ORC-27).

  The result is memoized on the entity's JSON text, so calling evaluate
  again with an unchanged entity returns the same object."
  ([entity] (evaluate entity nil))
  ([entity options]
   (let [rules (or (some-> options (gobj/get "rules")) "2014")]
     (when-not (contains? supported-rules rules)
       (throw (js/Error. (str "Unsupported rules edition: " rules
                              ". @dmv/pubdoor supports only \"2014\"."))))
     (let [text (if (string? entity) entity (js/JSON.stringify entity))
           {:keys [key value]} @memo]
       (if (= key text)
         value
         (let [value (evaluate* text)]
           (reset! memo {:key text :value value})
           value))))))
