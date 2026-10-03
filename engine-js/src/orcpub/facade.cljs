(ns orcpub.facade
  "The exported API of @pubdoor/dmv. See orc-alchemy docs/plan/02-engine-library.md.

  Nothing lazy crosses the boundary: every function takes and returns plain
  JS data. The conversion rules are the ones fixtures/README.md documents for
  expected.json (orcpub.oracle/->plain in scripts/orcpub/oracle.clj)."
  (:require [cljs.spec.alpha :as spec]
            [clojure.string :as str]
            [clojure.walk :as walk]
            [cognitect.transit :as transit]
            [goog.object :as gobj]
            [orcpub.entity :as entity]
            [orcpub.entity.strict :as se]
            [orcpub.template :as t]
            [orcpub.common :as common]
            [orcpub.dnd.e5 :as e5]
            [orcpub.dnd.e5.character :as char5e]
            [orcpub.dnd.e5.classes :as class5e]
            [orcpub.dnd.e5.event-handlers :as eh]
            [orcpub.dnd.e5.import-validation :as import-val]
            [orcpub.dnd.e5.template :as t5e]
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

(defn- shield?
  "True for an [item-key item] pair whose item is a shield."
  [[_ item]]
  (= :shield (:type item)))

(defn- armor-combos
  "Every carried armor × carried shield, each side also nil, as
  armor-calculations in subs.cljs does for ::char5e/best-armor-combo."
  [built all-armor-map]
  (let [ac-fn (char5e/armor-class-with-armor built)
        items (map (fn [k] [k (get all-armor-map k)])
                   (sort (keys (char5e/all-armor-inventory built))))
        shields (filter shield? items)
        armor (remove shield? items)]
    (vec
     (for [[armor-key armor-item] (conj (vec armor) [nil nil])
           [shield-key shield-item] (conj (vec shields) [nil nil])]
       {"armor" (some-> armor-key kw->str)
        "shield" (some-> shield-key kw->str)
        "ac" (ac-fn armor-item shield-item)}))))

(defn- armor-speeds
  "speed-with-armor evaluated unarmored (nil) and then with each carried
  armor. Like speed-section-2 in views.cljs, but it skips every item with
  :type :shield, as armor-combos does, and resolves homebrew armor through
  ::mi5e/all-armor-map. nil when nothing sets the attribute, such as the
  barbarian's Fast Movement."
  [built all-armor-map]
  (when-let [speed-fn (char5e/land-speed-with-armor built)]
    (let [armor (->> (sort (keys (char5e/all-armor-inventory built)))
                     (map (fn [k] [k (get all-armor-map k)]))
                     (remove shield?))]
      (vec
       ;; nil first on purpose: the old UI shows the unarmored speed first.
       (for [[armor-key armor-item] (cons [nil nil] armor)]
         {"armor" (some-> armor-key kw->str)
          "speed" (speed-fn armor-item)})))))

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
               "speed-with-armor" (armor-speeds built all-armor-map)
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

(defn- entity-text
  "The Transit-JSON text of an entity given as text or as the value
  JSON.parse returns for it."
  [entity]
  (if (string? entity) entity (js/JSON.stringify entity)))

(defn- evaluate* [text]
  (let [{:keys [template] :as content} @srd-template
        raw (char5e/from-strict (read-strict text))
        built (entity/build raw template)]
    (clj->js {"built" (built-values built content)
              "selections" (selections raw built template)})))

(def ^:private supported-rules #{"2014"})

(defn- check-rules!
  "Throws unless options.rules, which defaults to \"2014\", is supported."
  [options]
  (let [rules (or (some-> options (gobj/get "rules")) "2014")]
    (when-not (contains? supported-rules rules)
      (throw (js/Error. (str "Unsupported rules edition: " rules
                             ". @pubdoor/dmv supports only \"2014\"."))))))

(def ^:private memo (atom {:key nil :value nil}))

(defn ^:export evaluate
  "Builds a strict entity and returns {built, selections} as plain JS.

  entity is the strict entity as Transit-JSON: the text, or the value
  JSON.parse returns for it. options is {rules?, homebrew?}. rules defaults
  to \"2014\", the only edition 0.1 supports. homebrew is accepted and
  ignored until buildTemplate (ORC-27).

  Weapon bonuses read the hand slots in the entity's values,
  :orcpub.dnd.e5.character/main-hand-weapon and off-hand-weapon, each a
  weapon or item key. The Dueling fighting style's +2 damage applies only
  when the main hand holds a one-handed melee weapon and the off hand is
  set to something that is not a weapon, such as :shield. An empty off hand
  gives no bonus.

  The result is memoized on the entity's JSON text, so calling evaluate
  again with an unchanged entity returns the same object."
  ([entity] (evaluate entity nil))
  ([entity options]
   (check-rules! options)
   (let [text (entity-text entity)
         {:keys [key value]} @memo]
     (if (= key text)
       value
       (let [value (evaluate* text)]
         (reset! memo {:key text :value value})
         value)))))

;;; ---------------------------------------------------------------------------
;;; importCharacter, exportCharacter (orc-alchemy docs/plan/03-character-import-and-storage.md)
;;; ---------------------------------------------------------------------------

(defn- read-entity [entity]
  (read-strict (entity-text entity)))

(defn- write-entity
  "A strict entity or homebrew as verbose Transit-JSON, parsed: the format
  of the fixtures' .strict.json files and of evaluate's input."
  [strict]
  (js/JSON.parse (transit/write (transit/writer :json-verbose) strict)))

(defn- strip-ownership
  "Removes the old app's Datomic ids, which are on nearly every map, and
  the owner."
  [strict]
  (walk/postwalk #(if (map? %) (dissoc % :db/id ::se/owner) %) strict))

(defn- migrate-legacy-keys
  "R7: migrates legacy unnamespaced keys to namespaced ones (patch D1)."
  [raw]
  (if (spec/valid? ::char5e/unnamespaced-character raw)
    (char5e/add-namespaces raw)
    raw))

(defn- parse-xps
  "R5: a string xps → int, blank or invalid → 0, as the old server did
  (routes.clj:930). to-strict drops a string xps."
  [raw]
  (let [xps (get-in raw [::entity/values ::char5e/xps])]
    (if (string? xps)
      (assoc-in raw [::entity/values ::char5e/xps] (char5e/parse-int (str/trim xps)))
      raw)))

(defn ^:export importCharacter
  "Imports a character saved by the old app: the strict entity as
  Transit-JSON text or its parsed value. Applies the from-strict
  normalizations (R1 to R3, R6, R9), the legacy key migration (R7), and the
  xps fix (R5), and removes the old ids and owner.

  Returns {entity, legacyId}: entity in evaluate's input format, and
  legacyId the old top-level :db/id as a string, or null."
  [entity]
  (let [strict (read-entity entity)
        legacy-id (:db/id strict)
        raw (-> strict
                strip-ownership
                char5e/from-strict
                migrate-legacy-keys
                parse-xps)]
    #js {"entity" (write-entity (char5e/to-strict raw))
         "legacyId" (some-> legacy-id str)}))

(defn ^:export exportCharacter
  "Normalizes an entity with char5e/from-strict and serializes it with
  char5e/to-strict, as parsed verbose Transit-JSON. Selections stay arrays,
  so their order is kept."
  [entity]
  (write-entity (char5e/to-strict (char5e/from-strict (read-entity entity)))))

;;; ---------------------------------------------------------------------------
;;; parseOrcbrew (orc-alchemy docs/plan/04-homebrew.md)
;;;
;;; Homebrew crosses the boundary as the multi-plugin map, the old app's
;;; :plugins ({pack-name plugin}), in verbose Transit-JSON: the text, or the
;;; value JSON.parse returns for it, as for strict entities.
;;; ---------------------------------------------------------------------------

;; COPIED: events.cljs:3917-3954 (build-conflict-list). events.cljs cannot be
;; required. import-name is unused there too.
(defn- build-conflict-list
  [{:keys [internal-conflicts external-conflicts]} import-name]
  (let [;; Internal conflicts: same key appears in multiple sources within the import
        internal (map-indexed
                  (fn [idx {:keys [key content-type content-type-name sources]}]
                    {:id (str "internal-" idx)
                     :type :internal
                     :key key
                     :content-type content-type
                     :content-type-name content-type-name
                     :sources sources
                     ;; For internal, user picks which source to rename
                     :suggested-renames (mapv (fn [{:keys [source name]}]
                                                {:source source
                                                 :new-key (import-val/generate-new-key key source)})
                                              sources)})
                  internal-conflicts)

        ;; External conflicts: imported key conflicts with existing key
        external (map-indexed
                  (fn [idx {:keys [key content-type content-type-name
                                   import-source import-name
                                   existing-source existing-name]}]
                    {:id (str "external-" idx)
                     :type :external
                     :key key
                     :content-type content-type
                     :content-type-name content-type-name
                     :import-source import-source
                     :import-name import-name
                     :existing-source existing-source
                     :existing-name existing-name
                     ;; Suggested rename for the import
                     :suggested-new-key (import-val/generate-new-key key import-source)})
                  external-conflicts)]
    (vec (concat internal external))))

(defn ^:export parseOrcbrew
  "Runs .orcbrew text through the old importer, validate-import with
  auto-clean on, as the ::e5/import-plugin event does. A leading byte-order
  mark is removed first.

  options is {name?, existing?, strict?}. name is the pack name for a
  single-plugin file, the file name without .orcbrew in the old app, and
  defaults to \"Imported Content\". existing is the homebrew already loaded,
  for the external key-conflict check. strict selects the all-or-nothing
  strategy instead of the progressive one.

  Returns {success, data, log, conflicts, skipped}:
    data      existing with the file merged in, as homebrew, or null when
              success is false. As in the old app, a single-plugin file
              replaces the pack under name, and a multi-plugin file's packs
              merge into same-named packs one content type at a time
              (e5/merge-all-plugins)
    log       the old :import-log fields: changes, errors, skipped-items,
              key-conflicts and key-warnings, plus imported-count,
              skipped-count, message (the old app's notice) and, for a parse
              failure, parse-error, line and hint
    conflicts the key conflicts with suggested keys, as the old
              conflict-resolution modal lists them
    skipped   the items the progressive strategy left out, {key, errors}
  Key conflicts do not stop an import. The old app asked the user to
  resolve them before it loaded the data."
  ([text] (parseOrcbrew text nil))
  ([text options]
   (let [name (or (some-> options (gobj/get "name")) "Imported Content")
         existing (some-> options (gobj/get "existing") read-entity)
         result (import-val/validate-import
                 (str/replace-first text #"^\uFEFF" "")
                 {:strategy (if (some-> options (gobj/get "strict")) :strict :progressive)
                  :existing-plugins existing
                  :import-source-name name})
         data (:data result)
         ;; The ::e5/import-plugin handler's test (events.cljs:3858-3859) and merge (:3871-3873)
         multi? (and (spec/valid? ::e5/plugins data)
                     (not (spec/valid? ::e5/plugin data)))]
     #js {"success" (boolean (:success result))
          "data" (when (:success result)
                   (write-entity (if multi?
                                   (e5/merge-all-plugins existing data)
                                   (assoc existing name data))))
          "log" (clj->js
                 (->plain
                  (assoc (select-keys result [:changes :skipped-items :key-conflicts :key-warnings
                                              :imported-count :skipped-count
                                              :parse-error :line :hint])
                         :errors (if (:parse-error result)
                                   [(:error result)]
                                   (vec (:errors result)))
                         :message (import-val/format-import-result result))))
          "conflicts" (clj->js (->plain (build-conflict-list (:key-conflicts result) name)))
          "skipped" (clj->js (->plain (vec (:skipped-items result))))})))

;;; ---------------------------------------------------------------------------
;;; Mutations (event_handlers.cljc and the builder's handlers in events.cljs)
;;;
;;; Each takes a strict entity and returns a new one, as evaluate takes it.
;;; They do what the old builder does for the same click or input. Where the
;;; builder would ignore the input, they throw with the reason instead.
;;; ---------------------------------------------------------------------------

(defn- fail! [& parts]
  (throw (js/Error. (apply str parts))))

(defn- content
  "The template content for options {rules?, homebrew?}. homebrew is
  ignored until buildTemplate (ORC-27)."
  [options]
  (check-rules! options)
  @srd-template)

(defn- read-raw [entity]
  (char5e/from-strict (read-entity entity)))

(defn- write-raw [raw]
  (write-entity (char5e/to-strict raw)))

(defn- str->kw
  "The inverse of kw->str: \"name\" is :name, \"ns/name\" is :ns/name."
  [s]
  (let [i (.indexOf s "/")]
    (if (pos? i)
      (keyword (subs s 0 i) (subs s (inc i)))
      (keyword s))))

(defn- read-path
  "A path as evaluate's selections report it: an array of key strings."
  [path]
  (mapv #(if (string? %) (str->kw %) %) (js->clj path)))

(defn- show-path [path]
  (js/JSON.stringify (clj->js (->plain path))))

(defn- read-value
  "A value in the entity's Transit-JSON form: \"~:key\" is a keyword, and
  anything else is plain JSON."
  [value]
  (first (read-strict (js/JSON.stringify #js [value]))))

(defn- failed-prereqs
  "The labels of the prerequisites of option that built fails."
  [option built]
  (keep (fn [{:keys [::t/prereq-fn ::t/label]}]
          (when (and prereq-fn (not (prereq-fn built)))
            (or label "a prerequisite")))
        (::t/prereqs option)))

(def ^:private custom-ui-selections
  "Selections the builder edits with its own controls instead of
  :select-option, and the mutations that do the same."
  (merge {:class "setClass, addClass, removeClass, addLevel or removeLevel"
          :hit-points "setField"
          :ability-scores "setField"
          :asi "increaseAbility or decreaseAbility"}
         (zipmap char5e/equipment-keys
                 (repeat "addInventoryItem or removeInventoryItem"))))

(defn- ui-selection
  "The selection the builder shows at path: the available selections
  combined as the builder combines them (character_builder.cljs:1663),
  found by actual path."
  [raw built template path]
  (let [matches (filter #(= path (entity/actual-path %))
                        (entity/combine-selections
                         (entity/available-selections raw built template)))]
    (case (count matches)
      0 (fail! "No available selection at " (show-path path) ".")
      1 (first matches)
      (fail! "More than one selection at " (show-path path) "."))))

(defn- ui-option [selection path k]
  (or (some #(when (= k (::t/key %)) %) (::t/options selection))
      (fail! "The selection at " (show-path path) " has no option "
             (kw->str k) ".")))

(defn- option-click
  "The :select-option payload for a click on option k of the selection at
  path, as views_aux/option-selector-data and selection-section-data build
  it. Throws where the view or the handler (event_handlers.cljc:110) would
  do nothing, with the reason. Also returns the option, whose select-fn
  the payload leaves out."
  [raw template path k deselect?]
  (let [built (entity/build raw template)
        selection (ui-selection raw built template path)
        {:keys [::t/min ::t/max ::t/multiselect? ::t/ref]} selection
        _ (when-let [instead (custom-ui-selections (::t/key selection))]
            (fail! "The builder does not edit " (show-path path)
                   " by selecting options. Use " instead "."))
        option (ui-option selection path k)
        new-option-path (conj path k)
        selected? (boolean (get-in (entity/make-path-map raw) new-option-path))
        failed (failed-prereqs option built)
        meets-prereqs? (empty? failed)
        homebrew? (boolean (get-in raw [::entity/homebrew-paths
                                        (or ref (::entity/path selection))]))
        remaining (entity/count-remaining template raw selection)
        disable-select-new? (and multiselect? (not (pos? remaining)) (some? max))
        selectable? (or homebrew?
                        (and (or selected? meets-prereqs?)
                             (or (not disable-select-new?) selected?)))
        has-selections? (boolean (seq (::t/selections option)))
        view-multiselect? (or multiselect? ref (> min 1) (nil? max))
        what (str (kw->str k) " at " (show-path path))]
    (cond
      (and deselect? (not selected?))
      (fail! what " is not selected.")

      (and (not deselect?) selected?)
      (fail! what " is already selected.")

      ;; The view sends a click on a selected option only for these
      ;; (views_aux.cljc:60).
      (and deselect? (not view-multiselect?))
      (fail! "The builder cannot deselect " what
             ". Select another option instead."))
    (cond
      (not (or multiselect? (not selected?) has-selections?))
      (fail! "The builder cannot deselect " what ".")

      (not (or selected? meets-prereqs? homebrew?))
      (fail! "Cannot select " what ": it requires "
             (str/join ", " failed) ".")

      (not selectable?)
      (fail! "Cannot select " what ": no selections remain."))
    {:option option
     :payload {:option-path path
               :selected? selected?
               :selectable? selectable?
               :homebrew? homebrew?
               :meets-prereqs? meets-prereqs?
               :selection selection
               :option (dissoc option ::t/select-fn)
               :has-selections? has-selections?
               :built-template template
               :new-option-path new-option-path}}))

(defn- background-config
  "The raw background config that background option k was built from
  (options.cljc:2469 derives the key from the name)."
  [{:keys [backgrounds]} k]
  (or (some #(when (= k (common/name-to-kw (:name %))) %) backgrounds)
      (fail! "No background " (kw->str k) ".")))

(defn- add-background-equipment [raw content k]
  (eh/add-background-starting-equipment
   raw
   [:add-background-starting-equipment (background-config content k)]))

(defn- click
  "Applies a click on option k at path. A background option's select-fn
  (options.cljc:2483) dispatches :add-background-starting-equipment, which
  runs after the selection; this applies it directly."
  [raw content path k deselect?]
  (let [{:keys [option payload]} (option-click raw (:template content) path k deselect?)
        clicked (eh/select-option raw [:select-option payload])]
    (cond
      (not (::t/select-fn option)) clicked
      (= [:background] path) (add-background-equipment clicked content k)
      :else (fail! "Unsupported select-fn on " (kw->str k) " at "
                   (show-path path) "."))))

(defn ^:export emptyCharacter
  "The builder's new character (db.cljs:59): a level 1 barbarian with the
  standard ability scores and the barbarian's starting equipment."
  []
  (write-raw (char5e/set-class t5e/character
                               :barbarian
                               0
                               (class5e/barbarian-option nil nil nil nil nil))))

(defn ^:export select
  "Selects option optionKey of the selection whose actualPath is path, as
  a click in the builder does. Selecting a background also adds its
  starting equipment."
  ([entity path option-key] (select entity path option-key nil))
  ([entity path option-key options]
   (write-raw (click (read-raw entity) (content options)
                     (read-path path) (str->kw option-key) false))))

(defn ^:export deselect
  "Removes option optionKey from the selection whose actualPath is path, as
  a click on a selected option in the builder does. Throws where that
  click would not remove the option, for example on a single-select."
  ([entity path option-key] (deselect entity path option-key nil))
  ([entity path option-key options]
   (let [path (read-path path)
         k (str->kw option-key)
         clicked (click (read-raw entity) (content options) path k true)]
     (when (get-in (entity/make-path-map clicked) (conj path k))
       (fail! "The builder cannot deselect " (kw->str k) " at "
              (show-path path) "."))
     (write-raw clicked))))

(defn- value-key
  "A key of ::entity/values. A key without a namespace is in
  orcpub.dnd.e5.character, as all the builder's value fields are."
  [key]
  (let [k (str->kw key)]
    (if (namespace k) k (keyword "orcpub.dnd.e5.character" (name k)))))

(defn ^:export setValue
  "Sets a character value (::entity/values), as :update-value-field does
  (events.cljs:1249). value is in the entity's Transit-JSON form."
  [entity key value]
  (write-raw (assoc-in (read-raw entity)
                       [::entity/values (value-key key)]
                       (read-value value))))

(defn ^:export setField
  "Sets the value of the option at path, a selection's actualPath followed
  by the option key, as the hit point editor (:set-level-hit-points) and
  the ability score editor (:set-abilities) do. For a single-select, an
  option with a different key is replaced. For a multi-select, the option
  must already be selected. value is in the entity's Transit-JSON form."
  ([entity path value] (setField entity path value nil))
  ([entity path value options]
   (let [{:keys [template]} (content options)
         raw (read-raw entity)
         path (read-path path)
         _ (when (< (count path) 2)
             (fail! "setField needs a selection path and an option key, not "
                    (show-path path) "."))
         k (peek path)
         selection-path (pop path)
         built (entity/build raw template)
         _ (ui-option (ui-selection raw built template selection-path) selection-path k)
         v (read-value value)
         current (entity/get-option template raw selection-path)]
     (write-raw
      (if (sequential? current)
        (if (some #(= k (::entity/key %)) current)
          (entity/update-option template raw path #(assoc % ::entity/value v))
          (fail! (kw->str k) " at " (show-path selection-path) " is not selected."))
        (entity/update-option
         template raw path
         (fn [o]
           (if (= k (::entity/key o))
             (assoc o ::entity/value v)
             (with-meta {::entity/key k ::entity/value v} (meta o))))))))))

(defn- class-options
  "The template's class options by key, the options-map the builder passes
  to :set-class and :delete-class (character_builder.cljs:171)."
  [template]
  (let [s (some #(when (= :class (::t/key %)) %) (::t/selections template))]
    (zipmap (map ::t/key (::t/options s)) (::t/options s))))

(defn- class-option [template k]
  (or ((class-options template) k)
      (fail! "No class " (kw->str k) ".")))

(defn- classes [raw]
  (get-in raw [::entity/options :class]))

(defn- class-index [raw k]
  (or (first (keep-indexed #(when (= k (::entity/key %2)) %1) (classes raw)))
      (fail! "The character has no class " (kw->str k) ".")))

(defn- check-class-prereqs! [raw template option]
  (let [failed (failed-prereqs option (entity/build raw template))]
    (when (seq failed)
      (fail! "Cannot add " (kw->str (::t/key option)) ": it requires "
             (str/join ", " failed) "."))))

(defn- change-level [entity class-key delta options]
  (let [{:keys [template]} (content options)
        raw (read-raw entity)
        k (str->kw class-key)
        i (class-index raw k)
        level (count (get-in raw [::entity/options :class i ::entity/options :levels]))
        new-level (+ level delta)
        levels (some #(when (= :levels (::t/key %)) %)
                     (::t/selections (class-option template k)))
        max-level (count (::t/options levels))]
    (cond
      (< new-level 1)
      (fail! (kw->str k) " is level 1. Use removeClass to remove it.")

      (> new-level max-level)
      (fail! (kw->str k) " is already level " level ", the highest.")

      :else
      (write-raw (eh/set-class-level raw [:set-class-level i new-level])))))

(defn ^:export addLevel
  "Adds a level to class classKey, as the builder's level dropdown
  (:set-class-level) does."
  ([entity class-key] (addLevel entity class-key nil))
  ([entity class-key options] (change-level entity class-key 1 options)))

(defn ^:export removeLevel
  "Removes the highest level of class classKey and its selections, as the
  builder's level dropdown (:set-class-level) does."
  ([entity class-key] (removeLevel entity class-key nil))
  ([entity class-key options] (change-level entity class-key -1 options)))

(defn ^:export setClass
  "Replaces the class at index with classKey at level 1, as the builder's
  class dropdown (:set-class) does. For the first class, this also replaces
  the class starting equipment. Classes after the first must meet their
  prerequisites."
  ([entity index class-key] (setClass entity index class-key nil))
  ([entity index class-key options]
   (let [{:keys [template]} (content options)
         raw (read-raw entity)
         k (str->kw class-key)
         option (class-option template k)
         current (classes raw)]
     (cond
       (not (and (int? index) (< -1 index (count current))))
       (fail! "The character has no class at index " index ".")

       (some #(= k (::entity/key %)) current)
       (fail! "The character already has class " (kw->str k) ".")

       :else
       (do (when (pos? index) (check-class-prereqs! raw template option))
           (write-raw (eh/set-class raw [:set-class k index (class-options template)])))))))

(defn ^:export addClass
  "Adds class classKey at level 1, as the builder's \"Add Levels in Another
  Class\" (:add-class) and class dropdown do. The class must meet its
  prerequisites. add-class is in events.cljs, which is outside the build,
  so its body is repeated here."
  ([entity class-key] (addClass entity class-key nil))
  ([entity class-key options]
   (let [{:keys [template]} (content options)
         raw (read-raw entity)
         k (str->kw class-key)
         option (class-option template k)]
     (when (some #(= k (::entity/key %)) (classes raw))
       (fail! "The character already has class " (kw->str k) "."))
     (check-class-prereqs! raw template option)
     (write-raw (update-in raw [::entity/options :class] (fnil conj [])
                           {::entity/key k
                            ::entity/options {:levels [{::entity/key :level-1}]}})))))

(defn ^:export removeClass
  "Removes class classKey, as the builder's :delete-class does
  (events.cljs:1340). Removing the first class makes the next class first:
  it is reset to level 1 and gets that class's starting equipment.
  events.cljs is outside the build, so delete-class is repeated here."
  ([entity class-key] (removeClass entity class-key nil))
  ([entity class-key options]
   (let [{:keys [template]} (content options)
         raw (read-raw entity)
         k (str->kw class-key)
         i (class-index raw k)
         options-map (class-options template)
         updated (update-in raw [::entity/options :class]
                            (fn [cs] (vec (remove #(= k (::entity/key %)) cs))))
         new-first (get-in updated [::entity/options :class 0 ::entity/key])]
     (write-raw
      (if (and (zero? i) new-first (options-map new-first))
        (char5e/set-class updated new-first 0 (options-map new-first))
        updated)))))

(defn ^:export addStartingEquipment
  "Replaces the background starting equipment with that of background
  backgroundKey, as :add-background-starting-equipment does
  (event_handlers.cljc:53). select runs it when a background is selected."
  ([entity background-key] (addStartingEquipment entity background-key nil))
  ([entity background-key options]
   (write-raw (add-background-equipment (read-raw entity) (content options)
                                        (str->kw background-key)))))

(defn- check-inventory-key! [k]
  (when-not (some #{k} char5e/equipment-keys)
    (fail! (kw->str k) " is not an inventory selection.")))

(defn ^:export addInventoryItem
  "Adds one of item itemKey, equipped, to inventory selection selectionKey
  (for example \"weapons\"), as :add-inventory-item does."
  [entity selection-key item-key]
  (let [k (str->kw selection-key)]
    (check-inventory-key! k)
    (write-raw (eh/add-inventory-item (read-raw entity) k (str->kw item-key)))))

(defn ^:export removeInventoryItem
  "Removes item itemKey from inventory selection selectionKey, as
  :remove-inventory-item does."
  [entity selection-key item-key]
  (let [k (str->kw selection-key)]
    (check-inventory-key! k)
    (write-raw (eh/remove-inventory-item (read-raw entity)
                                         [:remove-inventory-item k (str->kw item-key)]))))

(defn- ability-key
  "An ability key. A key without a namespace, such as \"str\", is in
  orcpub.dnd.e5.character."
  [k]
  (let [kw (str->kw k)]
    (if (namespace kw) kw (keyword "orcpub.dnd.e5.character" (name kw)))))

(defn- ability-increase
  "The ability score improvement selection at path, the entity path of its
  picks, and how often each ability is picked, as ability-increases-component
  computes them (character_builder.cljs:830)."
  [raw template path]
  (let [built (entity/build raw template)
        selection (ui-selection raw built template path)
        _ (when-not (= :asi (::t/key selection))
            (fail! (show-path path) " is not an ability score improvement."))
        increases-path (entity/get-entity-path template raw path)]
    {:selection selection
     :built built
     :increases-path increases-path
     :increases (frequencies (map ::entity/key (get-in raw increases-path)))}))

(defn ^:export increaseAbility
  "Adds one to ability abilityKey in the ability score improvement at path,
  as the builder's plus button (:increase-ability-value) does. Throws where
  the button is disabled: an ability the selection does not offer, no picks
  left, a second pick when the picks must differ, or a score of 20."
  ([entity path ability] (increaseAbility entity path ability nil))
  ([entity path ability options]
   (let [{:keys [template]} (content options)
         raw (read-raw entity)
         path (read-path path)
         k (ability-key ability)
         {:keys [selection built increases-path increases]} (ability-increase raw template path)
         {:keys [::t/max ::t/different? ::t/options]} selection]
     (cond
       (not (some #(= k (::t/key %)) options))
       (fail! "The improvement at " (show-path path) " does not offer " (kw->str k) ".")

       (and (some? max) (<= max (apply + (vals increases))))
       (fail! "The improvement at " (show-path path) " has no picks left.")

       (and different? (pos? (increases k 0)))
       (fail! "The improvement at " (show-path path) " needs different abilities.")

       (<= 20 (get (char5e/ability-values built) k 0))
       (fail! (kw->str k) " is already 20.")

       :else
       (write-raw (update-in raw increases-path (fnil conj []) {::entity/key k}))))))

(defn ^:export decreaseAbility
  "Removes one pick of ability abilityKey from the ability score improvement
  at path, as the builder's minus button (:decrease-ability-value) does."
  ([entity path ability] (decreaseAbility entity path ability nil))
  ([entity path ability options]
   (let [{:keys [template]} (content options)
         raw (read-raw entity)
         path (read-path path)
         k (ability-key ability)
         {:keys [increases-path increases]} (ability-increase raw template path)]
     (when-not (pos? (increases k 0))
       (fail! (kw->str k) " is not picked in the improvement at " (show-path path) "."))
     (write-raw (update-in raw increases-path
                           (fn [picks] (common/remove-first #(= k (::entity/key %)) picks)))))))

;;; ---------------------------------------------------------------------------
;;; autofill (the builder's random character, events.cljs:311)
;;;
;;; events.cljs is outside the build, so its random character code is
;;; repeated here. Every random draw goes through rand, a seeded generator,
;;; so the dice and the shuffle are repeated too.
;;; ---------------------------------------------------------------------------

(defn- mulberry32
  "A seeded generator of floats in [0, 1). The old app uses Math.random,
  which cannot be seeded."
  [seed]
  (let [state (volatile! (bit-or seed 0))]
    (fn []
      (let [a (vswap! state #(bit-or (+ % 0x6D2B79F5) 0))
            t (js/Math.imul (bit-xor a (unsigned-bit-shift-right a 15)) (bit-or a 1))
            t (bit-xor (+ t (js/Math.imul (bit-xor t (unsigned-bit-shift-right t 7))
                                          (bit-or t 61)))
                       t)]
        (/ (unsigned-bit-shift-right (bit-xor t (unsigned-bit-shift-right t 14)) 0)
           4294967296)))))

(defn- random-generator
  "rand for options.seed, or Math.random without a seed."
  [seed]
  (cond (nil? seed) js/Math.random
        (and (int? seed) (<= -2147483648 seed 4294967295)) (mulberry32 seed)
        :else (fail! "autofill: seed must be a 32-bit integer, not " seed ".")))

(defn- rand-below [rand n]
  (js/Math.floor (* (rand) n)))

(defn- shuffle-with
  "Fisher-Yates, drawing from rand."
  [rand coll]
  (let [a (to-array coll)]
    (loop [i (dec (alength a))]
      (when (pos? i)
        (let [j (rand-below rand (inc i))
              x (aget a i)]
          (aset a i (aget a j))
          (aset a j x)
          (recur (dec i)))))
    (vec a)))

(defn- die-roll [rand sides]
  (inc (rand-below rand sides)))

(defn- standard-ability-rolls
  "4d6, dropping the lowest, for each ability (character.cljc:358)."
  [rand]
  (zipmap char5e/ability-keys
          (repeatedly 6 #(apply + (rest (sort (repeatedly 4 (fn [] (die-roll rand 6)))))))))

(def ^:private selection-randomizers
  "The selections that take a value, not an option pick
  (events.cljs:288). Each gives the update-fn for the selection."
  {:ability-scores (fn [rand _ _]
                     (fn [_] {::entity/key :standard-roll
                              ::entity/value (standard-ability-rolls rand)}))
   :hit-points (fn [rand {[_ class-kw] ::entity/path} built]
                 (fn [_] {::entity/key :roll
                          ::entity/value (die-roll rand (-> (char5e/levels built)
                                                            class-kw
                                                            :hit-die))}))})

(defn- random-sequential-selection
  "The first n options, n random (events.cljs:250)."
  [rand template raw {:keys [::t/options] :as selection}]
  (let [n (inc (rand-below rand (count options)))]
    (entity/update-option template raw (entity/actual-path selection)
                          (fn [_] (mapv (fn [{:keys [::t/key]}] {::entity/key key})
                                        (take n options))))))

(defn- candidates
  "The options random-selection may pick: prerequisites pass, not <none>
  or Custom (events.cljs:268), and, unlike the old loop, not already
  selected and not banned by a backtrack."
  [template raw built banned selection]
  (let [path (entity/actual-path selection)
        selected (set (selected-option-keys template raw selection))]
    (filter (fn [{:keys [::t/key] :as option}]
              (and (not (#{:none :custom} key))
                   (not (selected key))
                   (not (banned [path key]))
                   (entity/meets-prereqs? option built)))
            (entity/selection-options selection))))

(defn- random-selection
  "As many candidates as picks remain (events.cljs:263). A class selection
  with no candidate gets the fighter."
  [rand template banned raw {:keys [::t/key ::t/multiselect?] :as selection}]
  (let [built (entity/build raw template)
        picks (take (entity/count-remaining template raw selection)
                    (shuffle-with rand (candidates template raw built banned selection)))]
    (reduce (fn [raw {:keys [::t/key]}]
              (let [option {::entity/key key}]
                (entity/update-option template raw (conj (entity/actual-path selection) key)
                                      #(if multiselect? (conj (or % []) option) option))))
            raw
            (if (and (= :class key) (empty? picks))
              [{::t/key :fighter}]
              picks))))

(defn- fill-selection [rand template built banned raw {:keys [::t/key ::t/sequential?] :as selection}]
  (if-let [randomizer (selection-randomizers key)]
    (entity/update-option template raw (entity/actual-path selection)
                          (randomizer rand selection built))
    (if sequential?
      (random-sequential-selection rand template raw selection)
      (random-selection rand template banned raw selection))))

(defn- fillable?
  "Whether a selection with picks remaining has anything to pick."
  [template raw built banned {:keys [::t/key ::t/sequential?] :as selection}]
  (or (contains? selection-randomizers key)
      sequential?
      (= :class key) ; random-selection falls back to the fighter
      (seq (candidates template raw built banned selection))))

(defn- remove-option
  "raw without option k of the selection at path."
  [template raw path k]
  (let [p (entity/get-entity-path template raw path)
        v (get-in raw p)]
    (cond (and (map? v) (= k (::entity/key v)))
          (update-in raw (pop p) dissoc (peek p))

          (sequential? v)
          (assoc-in raw p (with-meta (vec (remove #(= k (::entity/key %)) v)) (meta v)))

          :else raw)))

(defn- backtrack
  "Undoes the choice that opened a selection nothing can fill: the option
  above one occurrence of it, such as the feat option of an ability score
  improvement when no feat is left, taking the last occurrence that
  autofill opened. The option is removed and banned, so the next step
  picks another. Only options autofill picked are undone, never those in
  the start entity (start-paths). nil when there is none.

  An occurrence's path interleaves selection and option keys, so the
  option above it is its path without the last key, and that option's
  selection is the path without the last two. Under a ref selection the
  path starts from the ref, so the parent is found by actual path."
  [template raw selections start-paths banned dead]
  (let [by-actual-path (group-by entity/actual-path selections)
        undo (distinct
              (keep (fn [s]
                      (let [path (entity/actual-path s)]
                        (first
                         (for [occurrence (reverse selections)
                               :when (and (= path (entity/actual-path occurrence))
                                          (pos? (or (::t/min occurrence) 0))
                                          ;; A top-level selection has no option above it.
                                          (< 2 (count (::entity/path occurrence))))
                               :let [option-path (pop (::entity/path occurrence))
                                     parent (first (by-actual-path (pop option-path)))
                                     parent-path (some-> parent entity/actual-path)
                                     k (peek option-path)]
                               :when (and parent
                                          (not (start-paths (conj parent-path k)))
                                          (some #{k} (selected-option-keys template raw parent)))]
                           [parent-path k]))))
                    dead))]
    (when (seq undo)
      {:raw (reduce (fn [raw [path k]] (remove-option template raw path k)) raw undo)
       :banned (into banned undo)})))

(defn- autofill-step
  "One step of the loop. Fills every combined selection with picks
  remaining that is not kept and has something to pick, as a round of the
  old loop does. When only selections with nothing to pick remain, it
  backtracks instead. nil when nothing is left to do."
  [rand template kept-paths start-paths {:keys [raw banned] :as state}]
  (let [built (entity/build raw template)
        selections (entity/available-selections raw built template)
        pending (filter #(and (pos? (entity/count-remaining template raw %))
                              (not (kept-paths (::entity/path %))))
                        (entity/combine-selections selections))
        {fill true dead false} (group-by #(boolean (fillable? template raw built banned %))
                                         pending)]
    (cond (seq fill)
          (assoc state :raw (reduce (partial fill-selection rand template built banned) raw fill))

          (seq dead)
          (backtrack template raw selections start-paths banned dead))))

(def ^:private autofill-steps
  "The old loop stops after 10 rounds (events.cljs:314). Each backtrack
  takes a step too, so this allows more. At the limit, autofill returns
  what it has filled, as the old loop does."
  20)

(def ^:private always-kept
  "The old button keeps :optional-content, the enabled plugins, with the
  locked components (events.cljs:342)."
  [[:optional-content]])

(defn- keep-options
  "A new entity with only the options at paths (events.cljs:299)."
  [template raw paths]
  (reduce (fn [kept path]
            (if-some [option (entity/get-option template raw path)]
              (entity/update-option template kept path (constantly option))
              kept))
          {}
          paths))

(defn- option-paths
  "The paths of every option in raw, as selection paths end in an option key."
  [raw]
  (set (map ::t/path (entity/flatten-options (::entity/options raw)))))

(defn ^:export autofill
  "Fills a character at random, as the builder's random character button
  does (events.cljs:311). Each step builds the character and fills every
  selection with picks remaining. Filling can open new selections, such as
  a class's, so it repeats, at most 20 steps. When a choice opens a
  selection nothing can fill, such as a feat when the SRD's one feat is
  taken, it undoes that choice and picks another, unlike the old button.
  It never undoes an option the entity already had.

  options is {seed?, keep?, keepAll?, rules?, homebrew?}. By default the old
  button's behaviour: the result keeps only the options at the keep paths
  (the builder's locked components, such as [\"race\"]) and the enabled
  plugins (:optional-content). It fills the rest, including the class and
  level, and drops the values such as the name. With keepAll, it keeps
  every option and value and fills only the selections with picks
  remaining. seed is a 32-bit integer; the same seed
  and entity give the same result. Without one, it uses Math.random.

  Names are not generated: character/random.cljc is not in the package."
  ([entity] (autofill entity nil))
  ([entity options]
   (let [{:keys [template]} (content options)
         rand (random-generator (some-> options (gobj/get "seed")))
         kept-paths (set (map read-path (some-> options (gobj/get "keep"))))
         raw (read-raw entity)
         start (if (some-> options (gobj/get "keepAll"))
                 raw
                 (keep-options template raw (concat kept-paths always-kept)))
         step (partial autofill-step rand template kept-paths (option-paths start))]
     (write-raw (loop [state {:raw start :banned #{}}, n 0]
                  (if-let [next-state (and (< n autofill-steps) (step state))]
                    (recur next-state (inc n))
                    (:raw state)))))))
