(ns orcpub.dnd.e5.modifiers-test
  (:require [clojure.test :refer [is deftest testing]]
            [orcpub.dnd.e5.character :as char5e]
            [orcpub.dnd.e5.modifiers :as dnd5-mods]
            [clojure.spec.test.alpha :as stest]))

;; Patch D8 (ORC-192): a scaled :frequency :amount map gives a number.
(deftest scaled-amount
  (let [bonuses {::char5e/wis 3 ::char5e/int -1 ::char5e/cha 0}
        amount (fn [m & [level]] (dnd5-mods/scaled-amount m bonuses 4 (or level 9)))]
    (testing "an ability key with or without its namespace"
      (is (= 3 (amount {:ability :wis})))
      (is (= 3 (amount {:ability ::char5e/wis}))))
    (testing "the engine applies only the minimum that the map holds"
      (is (= -1 (amount {:ability :int})))
      (is (= 0 (amount {:ability :cha})))
      (is (= 1 (amount {:ability :int :min 1})))
      (is (= 3 (amount {:ability :wis :min 1}))))
    (testing "the proficiency bonus"
      (is (= 4 (amount {:prof-bonus true})))
      (is (= 6 (amount {:prof-bonus true :plus 2}))))
    (testing "a level times a number, where times defaults to 1"
      (is (= 9 (amount {:level true})))
      (is (= 45 (amount {:level true :times 5})))
      (is (= 19 (amount {:level true :times 2 :plus 1}))))
    (testing "plus applies before the minimum"
      (is (= 2 (amount {:ability :wis :plus -3 :min 2})))
      (is (= 0 (amount {:ability :wis :plus -3}))))))

(deftest plugin-trait-cfg-keeps-the-old-modifier-for-a-number-amount
  (testing "only a map amount adds the scaled dependencies"
    (let [deps (fn [cfg] (set (:orcpub.modifiers/deps (dnd5-mods/plugin-trait-cfg cfg))))]
      (is (= (deps {:name "A"})
             (deps {:name "A" :frequency {:units :orcpub.dnd.e5.units/long-rest :amount 2}})
             (set (:orcpub.modifiers/deps (dnd5-mods/trait-cfg {:name "A"})))))
      (is (contains? (deps {:name "A" :frequency {:units :orcpub.dnd.e5.units/long-rest :amount {:prof-bonus true}}})
                     :prof-bonus)))))
