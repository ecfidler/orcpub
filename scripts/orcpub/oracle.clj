(ns orcpub.oracle
  "Phase A oracle (see docs/ts-rewrite-plan/HANDOFF-phase-a.md §4).

   Runs the OLD app's rules engine and its homebrew → template chain on the
   JVM so fixtures can be produced without a browser:

   * `entity/build` and the `character.cljc` accessors are `.cljc` and run
     on the JVM as-is (this is what `lein test` exercises).
   * The built-in races/backgrounds/languages and the plugin → template
     conversion live in `spell_subs.cljs` / `equipment_subs.cljs` as
     re-frame subscriptions. re-frame runs on the JVM (`re_frame/interop.clj`),
     so this namespace loads those two `.cljs` files verbatim through the
     Clojure reader, with only `js/` interop shimmed and the two subscriptions
     that fetch a logged-in user's custom magic items over HTTP replaced by
     the empty-list subscription the old app itself registers when there is
     no `js/window`. The template the oracle uses is therefore the one the
     old app computes for a user with no custom magic items.
   * `import_validation.cljs` (the `.orcbrew` auto-clean pipeline) is loaded
     the same way, with `cljs.reader` → `clojure.edn` and `cljs.spec.alpha`
     → `clojure.spec.alpha`.

   Everything here is a fixture-generation concern; nothing in this file is
   engine source and none of it ships in `@dmv/pubdoor`. The JSON encoding
   rules (`->plain`) are documented in fixtures/README.md and must be
   mirrored by the facade's one-pass extraction in M1."
  (:require [clojure.string :as str]
            [clojure.java.io :as io]
            [clojure.data.json :as json]
            [clojure.spec.alpha :as spec]
            [clojure.walk :as walk]
            [orcpub.common :as common]
            [cognitect.transit :as transit]
            [orcpub.entity :as entity]
            [orcpub.entity-spec :as es]
            [orcpub.template :as t]
            [orcpub.modifiers :as mods]
            [orcpub.dnd.e5 :as e5]
            [orcpub.dnd.e5.character :as char5e]
            [orcpub.dnd.e5.character.equipment :as equip5e]
            [re-frame.core :as rf]
            [re-frame.db :as rfdb])
  (:import [java.io ByteArrayInputStream ByteArrayOutputStream StringReader]
           [clojure.lang LineNumberingPushbackReader]))

;;; ---------------------------------------------------------------------------
;;; js/ shims for the .cljs files loaded below
;;; ---------------------------------------------------------------------------

(defn console-warn [& args]
  (binding [*out* *err*] (apply println "WARN" args)))

(defn console-error [& args]
  (binding [*out* *err*] (apply println "ERROR" args)))

(defn console-log [& args]
  (binding [*out* *err*] (apply println "LOG" args)))

;;; ---------------------------------------------------------------------------
;;; Loading .cljs files on the JVM
;;; ---------------------------------------------------------------------------

(defn- jvm-ns-form
  "Rewrites a ClojureScript ns form for the JVM. `libs` maps a cljs-only
   library to its JVM stand-in, or to nil to drop the require altogether.
   `strip-refer` names libraries whose :refer list is dropped (their referred
   vars are cljs-only). :require-macros and :import clauses are dropped."
  [[_ nm & clauses] libs strip-refer]
  (let [clauses (remove #(or (string? %) (map? %)) clauses)]
    (list* 'ns nm
           (keep (fn [[kind & specs :as clause]]
                   (case kind
                     :require
                     (cons :require
                           (keep (fn [spec]
                                   (let [spec (if (sequential? spec) (vec spec) [spec])
                                         lib (first spec)
                                         spec (if (contains? strip-refer lib)
                                                (vec (take 3 (remove #{:refer} (take-while #(not= % :refer) spec))))
                                                spec)]
                                     (if (contains? libs lib)
                                       (when-let [replacement (libs lib)]
                                         (assoc spec 0 replacement))
                                       spec)))
                                 specs))
                     (:require-macros :import) nil
                     clause))
                 clauses))))

(defn load-cljs-on-jvm!
  "Loads a .cljs file on the JVM as if it were .clj.
   opts:
     :rewrites    [[from to] ...] literal text substitutions applied before
                  reading (used to shim js/ interop)
     :libs        {lib replacement-or-nil} see `jvm-ns-form`
     :strip-refer #{lib ...}                see `jvm-ns-form`
     :skip?       (fn [form] ...) true skips a top-level form
     :transform   (fn [form] ...) applied to each top-level form before eval
   Returns the number of top-level forms evaluated."
  [path {:keys [rewrites libs strip-refer skip? transform]
         :or {rewrites [] libs {} strip-refer #{} skip? (constantly false)
              transform identity}}]
  (let [text (reduce (fn [t [from to]] (str/replace t from to)) (slurp path) rewrites)
        rdr (LineNumberingPushbackReader. (StringReader. text))
        eof (Object.)
        ns-form (read {:eof eof} rdr)]
    (assert (and (seq? ns-form) (= 'ns (first ns-form)))
            (str path " must start with an ns form"))
    (binding [*ns* *ns*]
      (eval (jvm-ns-form ns-form libs strip-refer))
      (loop [n 0]
        (let [form (read {:eof eof :read-cond :allow} rdr)]
          (if (identical? form eof)
            n
            (do (when-not (skip? form) (eval (transform form)))
                (recur (inc n)))))))))

(defn- lenient-fn
  "JavaScript ignores extra arguments; the JVM does not. re-frame calls a
   subscription's computation fn with (input-values query-vec), and many of
   the old handlers declare a single parameter. Give single-arity fn
   literals a rest parameter so they behave as they do in the browser."
  [form]
  (if (and (seq? form) (= 'fn (first form)) (vector? (second form))
           (not (some #{'&} (second form))))
    (list* 'fn (conj (second form) '& '_) (drop 2 form))
    form))

(defn- lenient-reg-sub [form]
  (if (and (seq? form) (= 'reg-sub (first form)))
    (concat (butlast form) [(lenient-fn (last form))])
    form))

(def ^:private js-rewrites
  [["(catch js/Error " "(catch Exception "]
   ["js/console.warn" "orcpub.oracle/console-warn"]
   ["js/console.error" "orcpub.oracle/console-error"]
   ["js/console.log" "orcpub.oracle/console-log"]])

(defn- form-mentions? [form & needles]
  (let [s (pr-str form)]
    (some #(str/includes? s %) needles)))

(defonce ^:private old-subs-loaded? (atom false))

(defn load-old-subs!
  "Loads spell_subs.cljs and equipment_subs.cljs so that
   (subscribe [::char5e/template]) yields the old app's template for the
   plugins in app-db. Idempotent. Run from the project root."
  []
  (when-not @old-subs-loaded?
    ;; re-frame warns on every subscribe outside a reactive context; that is
    ;; expected on the JVM.
    (rf/set-loggers! {:warn (fn [& _]) :log (fn [& _])})
    (load-cljs-on-jvm! "src/cljs/orcpub/dnd/e5/spell_subs.cljs"
                       {:rewrites js-rewrites
                        :libs '{reagent.ratom nil cljs-http.client nil}
                        :transform lenient-reg-sub})
    (load-cljs-on-jvm! "src/cljs/orcpub/dnd/e5/equipment_subs.cljs"
                       {:rewrites js-rewrites
                        :libs '{reagent.ratom nil cljs-http.client nil cljs.core.async nil}
                        :strip-refer '#{orcpub.dnd.e5.event-utils}
                        :transform lenient-reg-sub
                        ;; the two forms that fetch a user's custom magic
                        ;; items over HTTP (see ns docstring)
                        :skip? #(form-mentions? % "js/window" "http/get")})
    (rf/reg-sub :orcpub.dnd.e5.magic-items/custom-items (fn [_ _] []))
    (reset! old-subs-loaded? true)))

(defonce ^:private import-validation-loaded? (atom false))

(defn load-import-validation!
  "Loads import_validation.cljs (the .orcbrew parse/clean/validate pipeline)."
  []
  (when-not @import-validation-loaded?
    (load-cljs-on-jvm! "src/cljs/orcpub/dnd/e5/import_validation.cljs"
                       {:rewrites (into js-rewrites
                                        [[":cljs.spec.alpha/problems" ":clojure.spec.alpha/problems"]
                                         ["(.-message e)" "(.getMessage e)"]
                                         ["(js/parseInt (second line-match))" "(Long/parseLong (second line-match))"]])
                        :libs '{cljs.spec.alpha clojure.spec.alpha
                                cljs.reader clojure.edn}})
    (reset! import-validation-loaded? true)))

;;; ---------------------------------------------------------------------------
;;; JavaScript semantics the engine relies on
;;; ---------------------------------------------------------------------------

(defonce ^:private js-semantics-installed? (atom false))

(defn install-js-semantics!
  "Runtime replacements for engine fns that only work because JavaScript
   treats null leniently. Each keeps the browser's behaviour on the JVM; the
   engine source is untouched (see fixtures/README.md, findings).
   - options.cljc:848 proficiency-help: (> nil 1) — a homebrew subclass whose
     skill-options has no :choose. JS: null > 1 is false."
  []
  (when-not @js-semantics-installed?
    (let [help (resolve 'orcpub.dnd.e5.options/proficiency-help)]
      (alter-var-root help
                      (fn [f] (fn [num singular plural] (f (or num 0) singular plural)))))
    (reset! js-semantics-installed? true)))

;;; ---------------------------------------------------------------------------
;;; Template
;;; ---------------------------------------------------------------------------

(defn template-for-plugins
  "The old app's template for `plugins` ({source-name single-plugin-map}),
   i.e. what (subscribe [::char5e/template]) yields with that :plugins in
   app-db. {} gives the SRD-only template."
  [plugins]
  (load-old-subs!)
  (install-js-semantics!)
  (swap! rfdb/app-db assoc :plugins plugins)
  @(rf/subscribe [:orcpub.dnd.e5.character/template]))

(defn sub
  "Deref a registered subscription (after load-old-subs!)."
  [query-v]
  @(rf/subscribe query-v))

;;; ---------------------------------------------------------------------------
;;; .orcbrew import (mirrors the ::e5/import-plugin event, events.cljs)
;;; ---------------------------------------------------------------------------

(defn pack-name
  "The old UI names a pack after its file: everything before \".orcbrew\"."
  [path]
  (first (str/split (.getName (io/file path)) #".orcbrew")))

(defn import-orcbrew
  "Runs the old importer over an .orcbrew file's text as ::e5/import-plugin
   does (progressive strategy, auto-clean on) and merges the result into
   `existing-plugins` the way the event handler would.
   Returns {:result <validate-import result> :plugins <new plugins map>}.
   Key conflicts are reported in the result; the old UI would open the
   conflict-resolution modal instead of importing (see fixtures/README.md)."
  [source-name text existing-plugins]
  (load-import-validation!)
  (let [validate-import (resolve 'orcpub.dnd.e5.import-validation/validate-import)
        result (validate-import text {:strategy :progressive
                                      :auto-clean true
                                      :existing-plugins existing-plugins
                                      :import-source-name source-name})
        data (:data result)
        multi? (and (spec/valid? ::e5/plugins data)
                    (not (spec/valid? ::e5/plugin data)))]
    {:result result
     :plugins (cond (not (:success result)) existing-plugins
                    multi? (e5/merge-all-plugins existing-plugins data)
                    :else (assoc existing-plugins source-name data))}))

(defn plugins-for-orcbrew-files
  "Imports each file in order into an initially empty plugins map."
  [paths]
  (reduce (fn [plugins path]
            (:plugins (import-orcbrew (pack-name path) (slurp path) plugins)))
          {}
          paths))

;;; ---------------------------------------------------------------------------
;;; Plain-data (JSON) encoding — the rules the M1 facade must mirror
;;; ---------------------------------------------------------------------------

(defn kw->str [k]
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
       (catch ClassCastException _ (vec (sort-by pr-str coll)))))

(defn- plain-map [m]
  (let [ks (map simple-key->str (keys m))]
    (if (some #{::composite} ks)
      ;; e.g. spells-known is keyed by [class-name spell-key]
      {"__entries" (->> m
                        (map (fn [[k v]] [(->plain k) (->plain v)]))
                        (sort-by (comp pr-str first))
                        vec)}
      (into (sorted-map)
            (map (fn [[k v]] [(simple-key->str k) (->plain v)]))
            m))))

(defn ->plain
  "Convert a Clojure value to JSON-ready data:
   keyword → \"ns/name\" (or \"name\"), set → sorted vector, map → object
   with stringified keys (sorted; composite keys → {\"__entries\": [[k v] ...]}),
   ratio → double, fn → \"#fn\", anything else unknown → pr-str."
  [x]
  (cond (nil? x) nil
        (string? x) x
        (boolean? x) x
        (keyword? x) (kw->str x)
        (symbol? x) (str x)
        (ratio? x) (double x)
        (number? x) x
        (map? x) (plain-map x)
        (set? x) (sort-plain (map ->plain x))
        (sequential? x) (mapv ->plain x)
        (fn? x) "#fn"
        :else (pr-str x)))

;;; ---------------------------------------------------------------------------
;;; Built-character values (mirrors `character-subs`, subs.cljs:628-736)
;;; ---------------------------------------------------------------------------

(def plain-accessors
  "[json-key accessor]; the key is the ::char5e sub name from `character-subs`."
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
   the old app's ::mi5e/all-weapons-map subscription."
  [built]
  (let [all-weapons (sub [:orcpub.dnd.e5.magic-items/all-weapons-map])]
    (->> (merge (char5e/normal-weapons-inventory built)
                (char5e/magic-weapons-inventory built))
         keys
         sort
         (map (fn [k] [k (get all-weapons k)])))))

(defn- armor-combos
  "Every armor × shield combination, as subs.cljs `armor-calculations` does
   for ::char5e/best-armor-combo (armor and shields come from the carried
   armor split by :type, resolved through ::mi5e/all-armor-map)."
  [built]
  (let [all-armor-map (sub [:orcpub.dnd.e5.magic-items/all-armor-map])
        ac-fn (char5e/armor-class-with-armor built)
        carried (sort (keys (char5e/all-armor-inventory built)))
        ;; template_base.cljc `?armor-class-with-armor-base` adds
        ;; (::mi5e/magical-ac-bonus armor), which is nil for non-magical
        ;; armor. In the browser `(+ x nil)` is x; on the JVM it throws.
        ;; Default it to 0 so the JVM computes what the browser does.
        items (map (fn [k] [k (some-> (get all-armor-map k)
                                      (update :orcpub.dnd.e5.magic-items/magical-ac-bonus #(or % 0)))])
                   carried)
        shields (filter #(= :shield (:type (second %))) items)
        armor (remove #(= :shield (:type (second %))) items)]
    (vec
     (for [[armor-key armor-item] (conj (vec armor) [nil nil])
           [shield-key shield-item] (conj (vec shields) [nil nil])]
       {"armor" (some-> armor-key kw->str)
        "shield" (some-> shield-key kw->str)
        "ac" (ac-fn armor-item shield-item)}))))

(defn- weapon-table [built]
  (let [attack (char5e/weapon-attack-modifier-fn built)
        damage (char5e/weapon-damage-modifier-fn built)
        best-attack (char5e/best-weapon-attack-modifier-fn built)
        best-damage (char5e/best-weapon-damage-modifier-fn built)
        dual-wield? (char5e/dual-wield-weapon-fn built)
        has-prof? (char5e/has-weapon-prof built)]
    (into (sorted-map)
          (for [[k weapon] (carried-weapons built)]
            [(kw->str k)
             (if (nil? weapon)
               nil
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
  (into (sorted-map) (map (fn [k] [(if (keyword? k) (kw->str k) (str k)) (f k)])) ks))

(defn expected-values
  "The plain map written to <name>.expected.json: every accessor in
   `plain-accessors`, plus the function-valued attributes evaluated against
   fixed arguments (fixtures/README.md §Expected values)."
  [built]
  (let [levels (char5e/levels built)
        tool-profs (char5e/tool-proficiencies built)
        prepares (char5e/prepares-spells built)
        save-dc (char5e/spell-save-dc-fn built)
        spell-attack (char5e/spell-attack-modifier-fn built)
        tool-bonus (char5e/tool-bonus-fn built)
        class-level (char5e/class-level-fn built)
        prep-count (char5e/prepare-spell-count-fn built)]
    (-> (into (sorted-map)
              (map (fn [[k f]] [k (->plain (f built))]))
              plain-accessors)
        (assoc "armor-class-with-armor" (armor-combos built)
               "weapon-modifiers" (weapon-table built)
               "spell-save-dc" (keyed-table char5e/ability-keys save-dc)
               "spell-attack-modifier" (keyed-table char5e/ability-keys spell-attack)
               "class-level" (keyed-table (sort (keys levels)) class-level)
               "tool-bonus" (keyed-table (sort (if (map? tool-profs) (keys tool-profs) (seq tool-profs))) tool-bonus)
               "prepare-spell-count" (keyed-table (sort (keys prepares)) prep-count)))))

;;; ---------------------------------------------------------------------------
;;; Available selections (a flattened view, for authoring and for M1's
;;; `selections` output)
;;; ---------------------------------------------------------------------------

(defn- selected-option-keys [template raw selection]
  (let [selected (entity/get-option template raw (entity/actual-path selection))]
    (cond (sequential? selected) (mapv ::entity/key selected)
          (map? selected) (if-let [k (::entity/key selected)] [k] [])
          :else [])))

(defn selections-summary
  "entity/available-selections flattened to plain data: key, name, path,
   actualPath (doc 02 wrinkle 5), min/max, remaining, selected option keys."
  [raw built template]
  (swap! rfdb/app-db assoc :character raw) ; `none-option` prereq reads it
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

(defn unfilled-selections
  "Selections with a positive `remaining` count — what the old UI flags as
   still needing a choice. Used while authoring golden characters."
  [raw built template]
  (filter #(pos? (get % "remaining")) (selections-summary raw built template)))

;;; ---------------------------------------------------------------------------
;;; Template shape (for <pack>.template.json)
;;; ---------------------------------------------------------------------------

(defn- modifier-shape [{:keys [::mods/key ::mods/name ::mods/value]}]
  (cond-> {"key" (some-> key kw->str)}
    (string? name) (assoc "name" name)
    (or (number? value) (string? value) (keyword? value) (boolean? value))
    (assoc "value" (->plain value))))

(declare selection-shape)

(defn- option-shape [{:keys [::t/key ::t/name ::t/order ::t/selections ::t/modifiers ::t/prereqs]}]
  (cond-> {"key" (kw->str key)
           "name" name}
    order (assoc "order" order)
    (seq modifiers) (assoc "modifiers" (mapv modifier-shape (flatten modifiers)))
    (seq prereqs) (assoc "prereqs" (count prereqs))
    (seq selections) (assoc "selections" (mapv selection-shape selections))))

(defn selection-shape
  "Structural view of a template selection: keys, names, min/max, ref, tags,
   options (recursively) and each option's modifier keys. No functions."
  [{:keys [::t/key ::t/name ::t/min ::t/max ::t/ref ::t/tags ::t/order
           ::t/multiselect? ::t/sequential? ::t/require-value? ::t/options]}]
  (cond-> {"key" (kw->str key)
           "name" name
           "min" min
           "max" max
           "options" (mapv option-shape options)}
    ref (assoc "ref" (->plain ref))
    order (assoc "order" order)
    (seq tags) (assoc "tags" (->plain tags))
    multiselect? (assoc "multiselect" true)
    sequential? (assoc "sequential" true)
    require-value? (assoc "requireValue" true)))

(defn template-shape [template]
  (mapv selection-shape (::t/selections template)))

(defn template-summary
  "Per top-level selection: min/max and the option keys in template order."
  [template]
  (mapv (fn [{:keys [::t/key ::t/min ::t/max ::t/options]}]
          {"key" (kw->str key) "min" min "max" max
           "optionKeys" (mapv (comp kw->str ::t/key) options)})
        (::t/selections template)))

;; A structural diff of two template shapes. Selection nodes have "options"
;; children, option nodes have "selections" children; nodes are matched by
;; "key". The result contains only what differs: a node appears with its key,
;; the scalar fields that changed, the child diffs, and a "removed-<children>"
;; list of child keys present in the baseline only. A child missing from the
;; baseline is included whole. nil means identical.

(declare diff-node)

(defn- diff-children [baseline-children children child-key]
  (let [grandchild-key (if (= child-key "options") "selections" "options")
        by-key (into {} (map (juxt #(get % "key") identity)) baseline-children)
        present (set (map #(get % "key") children))
        removed (vec (remove present (map #(get % "key") baseline-children)))
        diffs (vec (keep (fn [child]
                           (if-let [b (get by-key (get child "key"))]
                             (diff-node b child grandchild-key)
                             child))
                         children))]
    (cond-> {}
      (seq diffs) (assoc child-key diffs)
      (seq removed) (assoc (str "removed-" child-key) removed))))

(defn- diff-node [baseline node child-key]
  (when (not= baseline node)
    (let [scalars (dissoc node child-key)
          baseline-scalars (dissoc baseline child-key)
          changed (into {} (filter (fn [[k v]] (not= v (get baseline-scalars k)))) scalars)
          removed-scalars (vec (remove (set (keys scalars)) (keys baseline-scalars)))]
      (cond-> (merge (diff-children (get baseline child-key) (get node child-key) child-key)
                     changed)
        true (assoc "key" (get node "key"))
        (seq removed-scalars) (assoc "removed-fields" removed-scalars)))))

(defn shape-delta
  "What `shape` adds to or changes in `baseline-shape` (both from
   `template-shape`)."
  [baseline-shape shape]
  (diff-children baseline-shape shape "selections"))

;;; ---------------------------------------------------------------------------
;;; Transit / JSON I/O
;;; ---------------------------------------------------------------------------

(defn read-transit [^String text]
  (transit/read (transit/reader (ByteArrayInputStream. (.getBytes text "UTF-8")) :json)))

(defn write-transit-verbose ^String [x]
  (let [out (ByteArrayOutputStream.)]
    (transit/write (transit/writer out :json-verbose) x)
    (.toString out "UTF-8")))

(defn- deep-sort [x]
  (cond (map? x) (into (sorted-map) (map (fn [[k v]] [k (deep-sort v)])) x)
        (sequential? x) (mapv deep-sort x)
        :else x))

(defn json-text ^String [data]
  (str (json/write-str (deep-sort data) :indent true :escape-slash false :escape-unicode false) "\n"))

(defn write-json-file [path data]
  (io/make-parents path)
  (spit path (json-text data)))

(defn write-json-gz-file [path data]
  (io/make-parents path)
  (with-open [out (java.util.zip.GZIPOutputStream. (io/output-stream path))]
    (.write out (.getBytes (json-text data) "UTF-8"))))

(defn read-json-gz-file [path]
  (with-open [in (java.util.zip.GZIPInputStream. (io/input-stream path))]
    (json/read (io/reader in))))

(defn- seqs->vectors
  "to-strict leaves lazy seqs in places (prepared-spells-by-class); the old
   server returned them as arrays. Write arrays."
  [x]
  (walk/postwalk #(if (seq? %) (vec %) %) x))

(defn write-strict-file
  "Writes a strict entity as pretty-printed Transit-JSON (verbose mode):
   valid JSON, lossless, readable by transit-js and cognitect.transit."
  [path strict]
  (write-json-file path (json/read-str (write-transit-verbose (seqs->vectors strict)))))

(defn read-strict-file [path]
  (read-transit (slurp path)))

;;; ---------------------------------------------------------------------------
;;; Characters
;;; ---------------------------------------------------------------------------

(defn legacy-normalize
  "The two import normalizations the engine does NOT inherit (doc 01 §C2,
   R5 and R7), applied to a raw entity after from-strict. This is what M1's
   importCharacter must do (patch D1); the oracle uses it to produce the
   expected values of the R5/R7 legacy fixtures.
   R5: a string ::char5e/xps → int (blank/invalid → 0), as routes.clj:930.
   R7: unqualified legacy keys (:str, :quantity, :character-name) →
       namespaced, using the character.cljc helpers the #_-disabled
       migration was built from."
  [raw]
  (let [r7 (fn [raw]
             (cond-> raw
               (get-in raw [::entity/options :ability-scores ::entity/value])
               char5e/add-ability-namespaces

               true
               (as-> r (reduce char5e/add-equipment-namespace r char5e/equipment-keys))

               (seq (::entity/values raw))
               (update ::entity/values
                       (fn [values]
                         (let [values (common/add-namespaces-to-keys "orcpub.dnd.e5.character" values)]
                           (reduce (fn [vs k]
                                     (if (sequential? (get vs k))
                                       (update vs k (fn [items]
                                                      (mapv (partial common/add-namespaces-to-keys
                                                                     "orcpub.dnd.e5.character.equipment")
                                                            items)))
                                       vs))
                                   values
                                   [::char5e/custom-equipment ::char5e/custom-treasure]))))))
        r5 (fn [raw]
             (let [xps (get-in raw [::entity/values ::char5e/xps])]
               (if (string? xps)
                 (assoc-in raw [::entity/values ::char5e/xps] (char5e/parse-int (str/trim xps)))
                 raw)))]
    (-> raw
        (cond-> (spec/valid? ::char5e/unnamespaced-character raw) r7)
        r5)))

(defn- fn-arities
  "#{n ...} of fixed arities, or nil when the fn is variadic."
  [f]
  (let [methods (.getDeclaredMethods (class f))]
    (when-not (some #(= "doInvoke" (.getName ^java.lang.reflect.Method %)) methods)
      (into #{} (comp (filter #(= "invoke" (.getName ^java.lang.reflect.Method %)))
                      (map #(alength (.getParameterTypes ^java.lang.reflect.Method %))))
            methods))))

(defn- js-arity
  "JavaScript pads missing arguments with undefined and drops extra ones; the
   JVM throws. Wrap `f` so it is called with the closest declared arity."
  [f]
  (if-let [arities (and (fn? f) (fn-arities f))]
    (fn [& args]
      (let [n (count args)]
        (if (arities n)
          (apply f args)
          (let [k (or (some->> arities (filter #(> % n)) seq (apply min))
                      (apply max arities))]
            (apply f (take k (concat args (repeat nil))))))))
    f))

(def ^:private fn-list-attributes
  [:damage-bonus-fns :attack-modifier-fns :ac-bonus-fns :ac-fns :default-skill-bonus-fns])

(defn with-js-arity
  "The old engine relies on JavaScript's lenient arity in places: the Dueling
   fighting style adds a 2-parameter fn to ?damage-bonus-fns, which
   template_base.cljc calls with one argument (options.cljc:1748,
   template_base.cljc:227). Evaluate those fn lists once and replace them with
   JS-lenient wrappers so the JVM computes what the browser does. This is
   patch D2 territory; the engine source is left untouched here."
  [built]
  (reduce (fn [b k]
            (let [v (es/entity-val b k)]
              (if (sequential? v) (assoc b k (mapv js-arity v)) b)))
          built
          fn-list-attributes))

(defn build-strict
  "strict entity → {:raw :built}; the same path M1's importCharacter +
   evaluate take (char5e/from-strict, legacy-normalize, then entity/build)."
  [strict template]
  (let [raw (legacy-normalize (char5e/from-strict strict))]
    {:raw raw
     :built (with-js-arity (entity/build raw template))}))

(defn dump-character
  "{:expected <expected.json data> :selections <selections.json data>}"
  [strict template]
  (let [{:keys [raw built]} (build-strict strict template)]
    {:expected (expected-values built)
     :selections (selections-summary raw built template)}))
