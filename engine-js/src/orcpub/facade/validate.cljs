(ns orcpub.facade.validate
  "The per-type homebrew validators (ORC-41): the checks the old builders
  run when they save an item, as plain functions.

  The old builders save through reg-save-homebrew (events.cljs:533-563) and
  ::selections5e/save-selection (events.cljs:624-677). Each normalizes the
  text, sets :key from the name, fills placeholders for missing fields, and
  checks the item with its type's spec. A custom magic item is checked by
  the old server's save-item with ::mi5e/magic-item (routes.clj:968-974).

  `check` does the same, with one difference: it does not fill
  placeholders. A placeholder such as \"[Missing Spell Name]\" hides a
  missing field, and the old spec rejected it anyway, because a name must
  start with a letter. It also keeps a :key the item already has, so an
  item with an explicit key, such as one of the 16 spells, keeps it."
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
  save checks. :homebrew? is true for the pack types, whose save sets :key
  from the name."
  {"race" {:spec ::races5e/homebrew-race :homebrew? true}
   "subrace" {:spec ::races5e/homebrew-subrace :homebrew? true}
   "class" {:spec ::class5e/homebrew-class :homebrew? true}
   "subclass" {:spec ::class5e/homebrew-subclass :homebrew? true}
   "background" {:spec ::bg5e/homebrew-background :homebrew? true}
   "feat" {:spec ::feats5e/homebrew-feat :homebrew? true}
   "spell" {:spec ::spells5e/homebrew-spell :homebrew? true}
   "language" {:spec ::langs5e/homebrew-language :homebrew? true}
   "invocation" {:spec ::class5e/homebrew-invocation :homebrew? true}
   "boon" {:spec ::class5e/homebrew-boon :homebrew? true}
   "selection" {:spec ::selections5e/homebrew-selection :homebrew? true}
   "monster" {:spec :orcpub.dnd.e5.monsters/homebrew-monster :homebrew? true}
   "encounter" {:spec ::encounters5e/encounter :homebrew? true}
   "magicItem" {:spec ::mi5e/magic-item}})

(defn- path-part [x]
  (if (keyword? x) (kw->str x) x))

(defn- missing-key
  "The key that pred requires, when pred is a spec/keys presence check:
  (fn [%] (contains? % k))."
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
  failed predicate as text."
  [{:keys [pred in]}]
  (if-let [k (missing-key pred)]
    {"path" (mapv path-part (conj (vec in) k)) "reason" "missing" "pred" (pr-str pred)}
    {"path" (mapv path-part in) "reason" "invalid" "pred" (pr-str pred)}))

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
  save would store it, with its text normalized and, for a pack type, its
  :key set."
  [type item]
  (let [{:keys [spec homebrew?]} (types type)
        item (cond-> (import-val/normalize-text-in-data item)
               homebrew? with-key)
        problems (vec (distinct
                       (concat (map problem (::spec/problems (spec/explain-data spec item)))
                               (when (= "selection" type) (duplicate-options item)))))]
    {:ok (empty? problems) :problems problems :item item}))
