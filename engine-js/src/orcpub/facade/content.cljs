(ns orcpub.facade.content
  "Writes the SRD content lists as JSON files at build time (ORC-36,
  ORC-42; orc-alchemy docs/plan/02-engine-library.md, the content row of
  the facade table and wrinkle 8).

  This is the main of the shadow-cljs build `content`, a Node script that
  `npm run build` runs after the release build. It is not part of
  dist/pubdoor.js. The lists ship as dist/content/<list>.json so that the
  app's browse pages and pickers load them without the engine chunk, and so
  that the monsters, which the character build never uses, stay out of the
  engine chunk (see orcpub.facade.no-monsters).

  Each item is converted by orcpub.facade.plain/->data, as the facade
  converts values: a keyword becomes \"ns/name\", a set a sorted array, and
  a map a string-keyed object. Functions are left out, and so are
  :modifiers and :selections, which hold engine objects rather than data.
  Any other value that is not plain data fails the build. The character's
  choices live in buildTemplate().shape."
  (:require ["fs" :as fs]
            ["path" :as path]
            [orcpub.common :as common]
            [orcpub.template :as t]
            [orcpub.dnd.e5.armor :as armor5e]
            [orcpub.dnd.e5.equipment :as equip5e]
            [orcpub.dnd.e5.magic-items :as mi5e]
            [orcpub.dnd.e5.monsters :as monsters5e]
            [orcpub.dnd.e5.spells :as spells5e]
            [orcpub.dnd.e5.weapons :as weapons5e]
            [orcpub.facade.plain :as plain]
            [orcpub.facade.template :as template]))

(def ^:private omitted-keys
  "Map keys whose values are engine objects (modifiers and template
  selections), not data."
  #{:modifiers :selections})

(defn- template-options
  "The options of the template's top-level selection selection-key, as
  {key, name}, without the builder's \"custom\" option."
  [template selection-key]
  (->> (::t/selections template)
       (some #(when (= selection-key (::t/key %)) %))
       ::t/options
       (remove #(= :custom (::t/key %)))
       (mapv (fn [{:keys [::t/key ::t/name]}]
               {:key key :name name}))))

(defn- with-key
  "item with :key, derived from its name as the template derives it when the
  item has none (backgrounds)."
  [item]
  (cond-> item
    (nil? (:key item)) (assoc :key (common/name-to-kw (:name item)))))

(defn content-lists
  "[file-name items] for each list, in the order the files are written.
  Each list is the SRD's, as the old app shows it with no homebrew loaded."
  []
  (let [{:keys [template content]} (template/build {})]
    [["classes" (template-options template :class)]
     ["races" (get content "races")]
     ["backgrounds" (map with-key (get content "backgrounds"))]
     ["feats" (template-options template :feats)]
     ["languages" (get content "languages")]
     ["spells" spells5e/spells]
     ["monsters" monsters5e/monsters]
     ["magic-items" mi5e/magic-items]
     ["weapons" weapons5e/weapons]
     ["ammunition" weapons5e/ammunition]
     ["armor" armor5e/armor]
     ["equipment" equip5e/equipment]
     ["treasure" equip5e/treasure]]))

(defn main
  "Writes <out-dir>/<list>.json for each list. out-dir defaults to
  dist/content. The .json files already in out-dir are removed first, so a
  list that is renamed or dropped is not published from an earlier build."
  [& [out-dir]]
  (let [out-dir (or out-dir "dist/content")]
    (fs/mkdirSync out-dir #js {:recursive true})
    (doseq [file (fs/readdirSync out-dir)
            :when (.endsWith file ".json")]
      (fs/unlinkSync (path/join out-dir file)))
    (doseq [[file-name items] (content-lists)]
      (fs/writeFileSync (path/join out-dir (str file-name ".json"))
                        (js/JSON.stringify
                         (clj->js (mapv #(plain/->data omitted-keys %) items)))))))
