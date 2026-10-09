(ns orcpub.facade.validators
  "The per-type homebrew validators (ORC-41): the checks the old builders
  run when they save an item, as plain functions.

  The old builders save through reg-save-homebrew (events.cljs:533-563) and
  ::selections5e/save-selection (events.cljs:624-677). Each normalizes the
  text, sets :key from the name, fills placeholders for missing fields, and
  checks the item with its type's spec. A custom magic item is checked by
  the old server's save-item with ::mi5e/magic-item (routes.clj:968-974).

  `check` does the same, with three differences.

  - It does not fill the missing fields (fill-all-missing-fields). A
    placeholder such as \"[Missing Spell Name]\" hides a missing name, and
    the old spec rejected it anyway, because a name must start with a
    letter. Two fills passed the spec: a spell's :level 0 and :school
    \"unknown\", and a trait's name \"[Missing Trait Name]\". So a spell
    without a level or school is reported, and a trait without a name
    passes as it is.
  - It keeps a :key the item already has. The old save always set :key
    from the name, but the new app renames keys (renameKey, ORC-70), and a
    later save must not undo the rename.
  - When the spec is spec/and of specs, it checks each part, so that it
    reports every problem. The old spec stopped at the first part that
    failed: for a spell, at ::spell before ::homebrew."
  (:require [cljs.spec.alpha :as spec]
            [clojure.string :as str]
            [orcpub.common :as common]
            [orcpub.dnd.e5.backgrounds :as bg5e]
            [orcpub.dnd.e5.classes :as class5e]
            [orcpub.dnd.e5.encounters :as encounters5e]
            [orcpub.dnd.e5.feats :as feats5e]
            [orcpub.dnd.e5.import-validation :as import-val]
            [orcpub.dnd.e5.languages :as langs5e]
            [orcpub.dnd.e5.magic-items :as mi5e]
            [orcpub.dnd.e5.races :as races5e]
            [orcpub.dnd.e5.selections :as selections5e]
            [orcpub.dnd.e5.spells :as spells5e]
            [orcpub.facade.plain :refer [kw->str]]))

;; COPIED: monsters.cljc:7-15. The pubdoor build aliases
;; orcpub.dnd.e5.monsters to orcpub.facade.no-monsters (ORC-42), so its
;; specs are not loaded. These define the same specs under the same keys.
(spec/def :orcpub.dnd.e5.monsters/name (spec/and string? common/starts-with-letter?))
(spec/def :orcpub.dnd.e5.monsters/key (spec/and keyword? common/keyword-starts-with-letter?))
(spec/def :orcpub.dnd.e5.monsters/option-pack string?)
(spec/def :orcpub.dnd.e5.monsters/die nat-int?)
(spec/def :orcpub.dnd.e5.monsters/die-count nat-int?)
(spec/def :orcpub.dnd.e5.monsters/modifier number?)
(spec/def :orcpub.dnd.e5.monsters/hit-points
  (spec/keys :req-un [:orcpub.dnd.e5.monsters/die :orcpub.dnd.e5.monsters/die-count]
             :opt-un [:orcpub.dnd.e5.monsters/modifier]))
(spec/def :orcpub.dnd.e5.monsters/homebrew-monster
  (spec/keys :req-un [:orcpub.dnd.e5.monsters/name
                      :orcpub.dnd.e5.monsters/key
                      :orcpub.dnd.e5.monsters/option-pack
                      :orcpub.dnd.e5.monsters/hit-points]))

(def types
  "Each validator's name, as the facade exports it, and the spec the old
  save checks. :as-is? is true for a magic item: the old server checked
  it as it was, with no text normalization and no :key."
  {"race" {:spec ::races5e/homebrew-race}
   "subrace" {:spec ::races5e/homebrew-subrace}
   "class" {:spec ::class5e/homebrew-class}
   "subclass" {:spec ::class5e/homebrew-subclass}
   "background" {:spec ::bg5e/homebrew-background}
   "feat" {:spec ::feats5e/homebrew-feat}
   "spell" {:spec ::spells5e/homebrew-spell}
   "language" {:spec ::langs5e/homebrew-language}
   "invocation" {:spec ::class5e/homebrew-invocation}
   "boon" {:spec ::class5e/homebrew-boon}
   "selection" {:spec ::selections5e/homebrew-selection}
   "monster" {:spec :orcpub.dnd.e5.monsters/homebrew-monster}
   "encounter" {:spec ::encounters5e/encounter}
   "magicItem" {:spec ::mi5e/magic-item :as-is? true}})

(defn- path-part [x]
  (if (keyword? x) (kw->str x) x))

(defn- missing-key
  "The key that pred requires, when pred is a spec/keys presence check:
  (fn [%] (contains? % k)). Adapted from events.cljs spec-error-message
  (516-530), which reads the key from a pred that starts with contains?.
  This one also finds the contains? inside the fn form, by its name."
  [pred]
  (some (fn [form]
          (when (and (seq? form)
                     (symbol? (first form))
                     (= "contains?" (name (first form))))
            (last form)))
        (tree-seq seq? seq pred)))

(defn- problem
  "A spec problem as {path, reason, pred}: path is the field's path of
  keys and indices, reason is \"missing\" or \"invalid\", and pred is the
  failed predicate as text. A blank string is missing, because a form
  sends an empty field as \"\"."
  [{:keys [pred in val]}]
  (if-let [k (missing-key pred)]
    {"path" (mapv path-part (conj (vec in) k)) "reason" "missing" "pred" (pr-str pred)}
    {"path" (mapv path-part in)
     "reason" (if (and (string? val) (str/blank? val)) "missing" "invalid")
     "pred" (pr-str pred)}))

(defn- spec-parts
  "The parts of spec when its form is (spec/and spec ...), else [spec]."
  [spec]
  (let [form (spec/form spec)]
    (if (and (seq? form)
             (symbol? (first form))
             (= "and" (name (first form)))
             (every? keyword? (rest form)))
      (rest form)
      [spec])))

(defn- duplicate-options
  "The selection save's own check (events.cljs:637-646): no two options
  may have names with the same key. The spec reports a missing or blank
  option name."
  [{:keys [options]}]
  (when (sequential? options)
    (let [option-key #(when (and (string? %) (not (str/blank? %))) (common/name-to-kw %))
          counts (frequencies (keep (comp option-key :name) options))]
      (for [[i {:keys [name]}] (map-indexed vector options)
            :let [k (option-key name)]
            :when (and k (< 1 (counts k)))]
        {"path" ["options" i "name"] "reason" "duplicate" "pred" "unique option name"}))))

(defn- with-key
  "item with :key from its name, as the old save sets it, unless it has a
  :key already or no name to derive one from."
  [{:keys [key name] :as item}]
  (if (or (some? key) (not (string? name)) (str/blank? name))
    item
    (assoc item :key (common/name-to-kw name))))

(defn check
  "Checks item, a map, as the old save of type does. Returns {:ok
  :problems :item}: problems as `problem` gives them, and item as the old
  save would store it: except for a magic item, its text normalized and
  its :key set."
  [type item]
  (let [{:keys [spec as-is?]} (types type)
        item (cond-> item
               (not as-is?) (-> import-val/normalize-text-in-data with-key))
        problems (vec (distinct
                       (concat (mapcat #(map problem (::spec/problems (spec/explain-data % item)))
                                       (spec-parts spec))
                               (when (= "selection" type) (duplicate-options item)))))]
    {:ok (empty? problems) :problems problems :item item}))
