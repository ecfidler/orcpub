(ns orcpub.facade
  "The exported API of @dmv/pubdoor. See docs/ts-rewrite-plan/02-engine-library.md."
  (:require [orcpub.dnd.e5.character :as char5e]
            ;; hello does not use the template namespace. It is required so
            ;; that the scaffold build loads the engine core the way evaluate
            ;; will, including options.cljc and its re-frame and reagent
            ;; dependencies.
            [orcpub.dnd.e5.template]))

(defn ^:export hello
  "Scaffold check for ORC-15. Returns a plain JS object built from engine
  data. Remove it when evaluate lands (ORC-16)."
  []
  #js {:greeting "hello"
       :abilities (into-array (map name char5e/ability-keys))})
