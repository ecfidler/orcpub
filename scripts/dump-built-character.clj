;; Phase A, M0: strict entity → expected built values.
;;
;; Usage (from the project root):
;;
;;   lein run -m clojure.main scripts/dump-built-character.clj \
;;       <in.strict.json> <out.expected.json> \
;;       [--selections <out.selections.json>] [--orcbrew <pack.orcbrew> ...]
;;
;;   scripts/oracle-env.sh scripts/dump-built-character.clj ...   ; no Leiningen/Clojars
;;
;; In a REPL: (load-file "scripts/dump-built-character.clj") then
;; (dump-built-character "fixtures/characters/fighter-1.strict.json" ...).
;;
;; <in.strict.json> is a strict entity as Transit-JSON (what
;; GET /dnd/5e/characters/:id returns; verbose or normal mode). The character
;; is built against the old app's template: SRD content plus every --orcbrew
;; pack, imported through the old importer in the order given.
;; See fixtures/README.md for the output format.
(load-file "scripts/orcpub/oracle.clj")

(ns dump-built-character
  (:require [orcpub.oracle :as oracle]
            [clojure.string :as str]))

(defn parse-args [args]
  (loop [[a & more] args
         opts {:orcbrew [] :positional []}]
    (cond (nil? a) opts
          (= a "--selections") (recur (rest more) (assoc opts :selections (first more)))
          (= a "--orcbrew") (recur (rest more) (update opts :orcbrew conj (first more)))
          (str/starts-with? a "--") (throw (ex-info (str "unknown option " a) {}))
          :else (recur more (update opts :positional conj a)))))

(defn dump-built-character
  [strict-path expected-path & {:keys [selections orcbrew] :or {orcbrew []}}]
  (let [plugins (oracle/plugins-for-orcbrew-files orcbrew)
        template (oracle/template-for-plugins plugins)
        strict (oracle/read-strict-file strict-path)
        {:keys [expected] sels :selections} (oracle/dump-character strict template)]
    (oracle/write-json-file expected-path expected)
    (when selections
      (oracle/write-json-file selections sels))
    (println "wrote" expected-path)
    (when selections (println "wrote" selections))))

(defn -main [& args]
  (let [{:keys [positional selections orcbrew]} (parse-args args)
        [in out] positional]
    (when-not (and in out)
      (binding [*out* *err*]
        (println "usage: dump-built-character.clj <in.strict.json> <out.expected.json> [--selections <out.selections.json>] [--orcbrew <pack.orcbrew> ...]"))
      (System/exit 2))
    (dump-built-character in out :selections selections :orcbrew orcbrew)))

(when (seq *command-line-args*)
  (apply -main *command-line-args*)
  (shutdown-agents))
