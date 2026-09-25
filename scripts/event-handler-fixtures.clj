;; ORC-19: the entities of test/cljc/orcpub/dnd/e5/event_handlers_test.clj as
;; Transit-JSON, for the vitest port in engine-js/test/mutations.test.ts.
;;
;;   lein run -m clojure.main scripts/event-handler-fixtures.clj
;;   scripts/oracle-env.sh scripts/event-handler-fixtures.clj   ; no Leiningen/Clojars
;;
;; Reads the test file without running it. Each deftest that binds `strict`
;; in its first let gives engine-js/test/fixtures/event-handlers/<test>.json.
;; The raw `character` def gives character.json, through entity/to-strict.
(ns event-handler-fixtures
  (:require [clojure.java.io :as io]
            [clojure.data.json :as json]
            [cognitect.transit :as transit]
            [orcpub.entity :as entity])
  (:import [java.io ByteArrayOutputStream PushbackReader]))

(def test-file "test/cljc/orcpub/dnd/e5/event_handlers_test.clj")
(def out-dir "engine-js/test/fixtures/event-handlers")

;; The test file's aliases, so that ::se/key and the like read.
(doseq [[a n] '{entity orcpub.entity se orcpub.entity.strict t orcpub.template
                t5e orcpub.dnd.e5.template eh orcpub.dnd.e5.event-handlers
                char-rand orcpub.dnd.e5.character.random}]
  (alias a (create-ns n)))

(defn read-forms [path]
  (with-open [r (PushbackReader. (io/reader path))]
    (binding [*read-eval* false]
      (doall (take-while #(not= % ::eof) (repeatedly #(read {:eof ::eof} r)))))))

(defn verbose-json [x]
  (let [out (ByteArrayOutputStream.)]
    (transit/write (transit/writer out :json-verbose) x)
    (json/read-str (.toString out "UTF-8"))))

(defn write! [file x]
  (let [path (str out-dir "/" file)]
    (io/make-parents path)
    (spit path (str (json/write-str (verbose-json x) :indent true :escape-slash false) "\n"))
    (println "wrote" path)))

(defn first-let-binding [body sym]
  (some (fn [form]
          (when (and (seq? form) (= 'let (first form)))
            (let [bindings (partition 2 (second form))]
              (some (fn [[s v]] (when (= s sym) v)) bindings))))
        body))

(doseq [form (read-forms test-file)
        :when (seq? form)]
  (case (first form)
    def (when (= 'character (second form))
          (write! "character.json" (entity/to-strict (nth form 2))))
    deftest (when-let [strict (first-let-binding (drop 2 form) 'strict)]
              (write! (str (second form) ".json") strict))
    nil))
