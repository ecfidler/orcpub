;; Phase A, M3: the old-spec acceptance check for @pubdoor/dmv's .orcbrew
;; export (contract C1, ORC-31).
;;
;; Usage (from the project root), on the directory that
;; engine-js/scripts/write-orcbrew-exports.mjs writes:
;;
;;   lein run -m clojure.main scripts/check-orcbrew-exports.clj <dir>
;;   scripts/oracle-env.sh scripts/check-orcbrew-exports.clj <dir>
;;
;; Each <dir>/*.orcbrew must be a valid ::e5/plugins and each
;; <dir>/single/*.orcbrew a valid ::e5/plugin. The old importer
;; (oracle/import-orcbrew) must then import every file with no auto-clean
;; changes, no skipped items, no errors, no key conflicts or warnings, and
;; the data unchanged. Exits 1 if any file fails.
(load-file "scripts/orcpub/oracle.clj")

(ns check-orcbrew-exports
  (:require [orcpub.oracle :as oracle]
            [orcpub.dnd.e5 :as e5]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.spec.alpha :as spec]))

(defn orcbrew-files
  "The .orcbrew files directly in dir, sorted by path."
  [dir]
  (sort-by str (filter #(.endsWith (.getName %) ".orcbrew") (.listFiles (io/file dir)))))

(defn problems
  "Why file fails the check against spec-k and the old importer, or empty."
  [file spec-k]
  (let [text (slurp file)
        data (edn/read-string text)
        {:keys [success errors had-errors changes skipped-items key-conflicts key-warnings] :as result}
        (:result (oracle/import-orcbrew (oracle/pack-name (str file)) text {}))]
    (cond-> []
      (not (spec/valid? spec-k data)) (conj (spec/explain-str spec-k data))
      (not success) (conj (str "import failed: " (pr-str (select-keys result [:error :errors]))))
      (seq changes) (conj (str "auto-clean changes: " (pr-str changes)))
      (seq skipped-items) (conj (str "skipped items: " (pr-str skipped-items)))
      (and success (seq errors)) (conj (str "import errors: " (pr-str errors)))
      had-errors (conj "the importer reports errors (:had-errors)")
      (some seq (vals key-conflicts)) (conj (str "key conflicts: " (pr-str key-conflicts)))
      (seq key-warnings) (conj (str "key warnings: " (pr-str key-warnings)))
      (and success (not= data (:data result))) (conj "the old importer changed the data"))))

(let [dir (first *command-line-args*)
      checks (concat (map vector (orcbrew-files dir) (repeat ::e5/plugins))
                     (map vector (orcbrew-files (io/file dir "single")) (repeat ::e5/plugin)))
      failures (keep (fn [[file spec-k]]
                       (when-let [ps (seq (problems file spec-k))]
                         [file ps]))
                     checks)]
  (when (empty? checks)
    (println "No .orcbrew files in" dir)
    (System/exit 1))
  (doseq [[file ps] failures]
    (println "FAIL" (str file))
    (doseq [p ps] (println "  " p)))
  (println (count checks) "exports checked," (count failures) "failed")
  (System/exit (if (empty? failures) 0 1)))
