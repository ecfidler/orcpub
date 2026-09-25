(ns orcpub.facade
  "The exported API of @dmv/pubdoor. See docs/ts-rewrite-plan/02-engine-library.md.

  Nothing lazy crosses the boundary: every function takes and returns plain
  JS data. The conversion rules are the ones fixtures/README.md documents for
  expected.json (orcpub.oracle/->plain in scripts/orcpub/oracle.clj)."
  (:require [cljs.spec.alpha :as spec]
            [clojure.string :as str]
            [clojure.walk :as walk]
            [cognitect.transit :as transit]
            [goog.object :as gobj]
            [re-frame.db]
            [orcpub.entity :as entity]
            [orcpub.entity.strict :as se]
            [orcpub.template :as t]
            [orcpub.common :as common]
            [orcpub.dnd.e5.character :as char5e]
            [orcpub.dnd.e5.classes :as class5e]
            [orcpub.dnd.e5.event-handlers :as eh]
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

(defn- seed-app-db!
  "The none-option prerequisite reads (:character @app-db)
  (options.cljc:2077). Seed it until patch D2 (ORC-22) reads the entity."
  [raw]
  (swap! re-frame.db/app-db assoc :character raw))

(defn- build-seeded
  "entity/build, with app-db seeded for the prerequisites that read it."
  [raw template]
  (seed-app-db! raw)
  (entity/build raw template))

(defn- selections
  "entity/available-selections flattened to plain data, in the order the
  engine returns them."
  [raw built template]
  (seed-app-db! raw)
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
                             ". @dmv/pubdoor supports only \"2014\"."))))))

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
   (check-rules! options)
   (let [text (entity-text entity)
         {:keys [key value]} @memo]
     (if (= key text)
       value
       (let [value (evaluate* text)]
         (reset! memo {:key text :value value})
         value)))))

;;; ---------------------------------------------------------------------------
;;; importCharacter, exportCharacter (docs/ts-rewrite-plan/03-character-import-and-storage.md)
;;; ---------------------------------------------------------------------------

(defn- read-entity [entity]
  (read-strict (entity-text entity)))

(defn- write-entity
  "A strict entity as verbose Transit-JSON, parsed: the format of the
  fixtures' .strict.json files and of evaluate's input."
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
  (let [built (build-seeded raw template)
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
  (options.cljc:2474 derives the key from the name)."
  [{:keys [backgrounds]} k]
  (or (some #(when (= k (common/name-to-kw (:name %))) %) backgrounds)
      (fail! "No background " (kw->str k) ".")))

(defn- add-background-equipment [raw content k]
  (eh/add-background-starting-equipment
   raw
   [:add-background-starting-equipment (background-config content k)]))

(defn- click
  "Applies a click on option k at path. A background option's select-fn
  (options.cljc:2488) dispatches :add-background-starting-equipment, which
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
         built (build-seeded raw template)
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
  (let [failed (failed-prereqs option (build-seeded raw template))]
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
  (let [built (build-seeded raw template)
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
