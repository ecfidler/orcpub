;; Phase A, M0: .orcbrew pack → the old app's template shape.
;;
;; Usage (from the project root):
;;
;;   lein run -m clojure.main scripts/dump-template.clj <pack.orcbrew> <out.template.json>
;;   lein run -m clojure.main scripts/dump-template.clj --baseline <out.template.json.gz>
;;   lein run -m clojure.main scripts/dump-template.clj --summary <pack.orcbrew> <out.summary.json>
;;
;;   scripts/oracle-env.sh scripts/dump-template.clj ...     ; no Leiningen/Clojars
;;
;; The pack is run through the old importer (import_validation.cljs, progressive
;; strategy, auto-clean on) exactly as the ::e5/import-plugin event does, the
;; result is placed in app-db as the only loaded pack, and the old subscription
;; chain (spell_subs.cljs, equipment_subs.cljs → ::char5e/template) produces the
;; template. The output records the import log, the content the pack
;; contributed, and the template's structural delta against the SRD-only
;; baseline (--baseline writes that baseline, gzipped: it is ~6 MB of JSON).
;; --summary writes the same minus the template delta, for packs whose text
;; cannot be committed (a user's full all-content export): keys and names only.
;; It also strips a leading byte-order mark, which browsers strip before the
;; old importer ever sees the text. See fixtures/README.md.
(load-file "scripts/orcpub/oracle.clj")

(ns dump-template
  (:require [orcpub.oracle :as oracle]
            [orcpub.template :as t]
            [clojure.string :as str]))

(def content-subs
  "The intermediate subscriptions of the chain, and the field that names each item."
  [["races" :orcpub.dnd.e5.races/races :key]
   ["backgrounds" :orcpub.dnd.e5.backgrounds/backgrounds :name]
   ["classes" :orcpub.dnd.e5.classes/classes :orcpub.template/key]
   ["feats" :orcpub.dnd.e5.feats/feats :key]
   ["languages" :orcpub.dnd.e5.languages/languages :key]
   ["invocations" :orcpub.dnd.e5.classes/invocations :key]
   ["boons" :orcpub.dnd.e5.classes/boons :key]
   ["plugin-spells" :orcpub.dnd.e5.spells/plugin-spells :key]
   ["plugin-subraces" :orcpub.dnd.e5.races/plugin-subraces :key]
   ["plugin-subclasses" :orcpub.dnd.e5.classes/plugin-subclasses :key]
   ["plugin-selections" :orcpub.dnd.e5.selections/plugin-selections :key]
   ["plugin-monsters" :orcpub.dnd.e5.monsters/plugin-monsters :key]])

(defn content-summary []
  (into (sorted-map)
        (map (fn [[label query-v field]]
               [label (mapv (fn [item] (oracle/->plain (get item field)))
                            (oracle/sub [query-v]))]))
        content-subs))

(defn plugins-summary [plugins]
  (into (sorted-map)
        (map (fn [[source plugin]]
               [source (into (sorted-map)
                             (keep (fn [[type-k items]]
                                     (when (map? items)
                                       [(oracle/kw->str type-k)
                                        (vec (sort (map oracle/kw->str (keys items))))])))
                             plugin)]))
        plugins))

(defn import-summary [{:keys [success parse-error error line hint errors changes
                              skipped-items key-conflicts imported-count skipped-count]}]
  (oracle/->plain
   (cond-> {:success (boolean success)
            :changes (vec changes)
            :skipped-items (mapv #(select-keys % [:key :errors]) skipped-items)
            :key-conflicts key-conflicts}
     parse-error (assoc :parse-error true :error error :line line :hint hint)
     (seq errors) (assoc :errors (vec errors))
     imported-count (assoc :imported-count imported-count)
     skipped-count (assoc :skipped-count skipped-count))))

(defn baseline-shape []
  (oracle/template-shape (oracle/template-for-plugins {})))

(defn dump-baseline [out-path]
  (let [template (oracle/template-for-plugins {})]
    (oracle/write-json-gz-file out-path {"summary" (oracle/template-summary template)
                                         "shape" (oracle/template-shape template)})
    (println "wrote" out-path)))

(defn dump-template
  ([pack-path out-path] (dump-template pack-path out-path (baseline-shape)))
  ([pack-path out-path baseline]
   (let [name (oracle/pack-name pack-path)
         {:keys [result plugins]} (oracle/import-orcbrew name (slurp pack-path) {})
         template (oracle/template-for-plugins plugins)]
     (oracle/write-json-file
      out-path
      {"pack" name
       "import" (import-summary result)
       "plugins" (plugins-summary plugins)
       "content" (content-summary)
       "templateSummary" (oracle/template-summary template)
       "templateDelta" (oracle/shape-delta baseline (oracle/template-shape template))})
     (println "wrote" out-path))))

(defn dump-summary [pack-path out-path]
  (let [name (oracle/pack-name pack-path)
        raw-text (slurp pack-path)
        text (str/replace-first raw-text #"^\uFEFF" "")
        {:keys [result plugins]} (oracle/import-orcbrew name text {})
        template (oracle/template-for-plugins plugins)]
    (oracle/write-json-file
     out-path
     {"pack" name
      "bomStripped" (not= raw-text text)
      "bytes" (.length (clojure.java.io/file pack-path))
      "import" (import-summary result)
      "plugins" (plugins-summary plugins)
      "content" (content-summary)
      "templateSummary" (oracle/template-summary template)})
    (println "wrote" out-path)))

(defn -main [& args]
  (cond (= (first args) "--baseline")
        (dump-baseline (second args))

        (= (first args) "--summary")
        (apply dump-summary (rest args))

        (= 2 (count args))
        (apply dump-template args)

        :else
        (do (binding [*out* *err*]
              (println "usage: dump-template.clj <pack.orcbrew> <out.template.json> | --baseline <out.json.gz> | --summary <pack.orcbrew> <out.summary.json>"))
            (System/exit 2))))

(when (seq *command-line-args*)
  (apply -main *command-line-args*)
  (shutdown-agents))
