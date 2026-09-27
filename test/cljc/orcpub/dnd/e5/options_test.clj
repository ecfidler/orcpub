(ns orcpub.dnd.e5.options-test
  (:require [clojure.test :refer [is deftest testing]]
            [clojure.spec.alpha :as spec]
            [orcpub.dnd.e5.options :as opt]
            [orcpub.dnd.e5.character :as char5e]
            [orcpub.dnd.e5.magic-items :as mi5e]
            [orcpub.dnd.e5.template :as t5e]
            [orcpub.dnd.e5.weapons :as weapons]
            [orcpub.template :as t]
            [orcpub.entity :as entity]))

(deftest test-total-slots
  (is (= {1 2} (opt/total-slots 3 3)))
  (is (= {1 4
          2 3
          3 3
          4 1}
         (opt/total-slots 20 3))))

;; -- feat-prereqs --

(deftest feat-prereqs-ability-prereq
  (testing "ability key produces ability prereq with min 13"
    (let [result (opt/feat-prereqs [::char5e/str] nil {})]
      (is (= 1 (count result)))
      (is (= "Requires STR 13 or higher" (::t/label (first result))))
      (is (fn? (::t/prereq-fn (first result)))))))

(deftest feat-prereqs-spellcasting-prereq
  (testing ":spellcasting produces can-cast-spell prereq"
    (let [result (opt/feat-prereqs [:spellcasting] nil {})]
      (is (= 1 (count result)))
      (is (= "Requires the ability to cast at least one spell."
             (::t/label (first result)))))))

(deftest feat-prereqs-armor-prereq
  (testing "non-ability non-spellcasting key produces armor prereq"
    (let [result (opt/feat-prereqs [:heavy] nil {})]
      (is (= 1 (count result)))
      (is (re-find #"(?i)heavy" (::t/label (first result)))))))

(deftest feat-prereqs-race-prereq-from-map
  (testing "race prereq resolves names from race-map parameter"
    (let [race-map {:elf {:name "Elf"} :dwarf {:name "Dwarf"}}
          path-prereqs {:race {:elf true :dwarf false}}
          result (opt/feat-prereqs [] path-prereqs race-map)
          labels (map ::t/label result)]
      ;; Only :elf has truthy value, :dwarf is false
      (is (= 1 (count result)))
      (is (some #(re-find #"Elf" %) labels)))))

(deftest feat-prereqs-no-race-prereq-when-empty
  (testing "no race prereqs when path-prereqs has no :race key"
    (let [result (opt/feat-prereqs [] {} {:elf {:name "Elf"}})]
      (is (empty? result))))
  (testing "no race prereqs when race map values are all false"
    (let [result (opt/feat-prereqs [] {:race {:elf false}} {:elf {:name "Elf"}})]
      (is (empty? result)))))

(deftest feat-prereqs-mixed-ability-and-race
  (testing "both ability and race prereqs combine"
    (let [race-map {:human {:name "Human"}}
          path-prereqs {:race {:human true}}
          result (opt/feat-prereqs [::char5e/str] path-prereqs race-map)]
      ;; 1 ability + 1 race
      (is (= 2 (count result))))))

;; -- patch D2: no app-db reads --

(def ^:private test-blade
  "A one-handed melee weapon that only a custom weapons map knows."
  {:name "Test Blade"
   :key :test-blade
   ::weapons/type :martial
   ::weapons/melee? true
   ::weapons/damage-type :slashing
   ::weapons/damage-die 8
   ::weapons/damage-die-count 1})

(def ^:private custom-weapons-map
  (assoc mi5e/all-weapons-map :test-blade test-blade))

(defn- fighter [fighting-style main-hand off-hand]
  {::entity/options {:class [{::entity/key :fighter
                              ::entity/options {:fighting-style [{::entity/key fighting-style}]}}]}
   ::entity/values {::char5e/main-hand-weapon main-hand
                    ::char5e/off-hand-weapon off-hand}})

(defn- damage [raw template weapon]
  ((char5e/weapon-damage-modifier-fn (entity/build raw template)) weapon false))

(deftest dueling-damage-bonus
  (let [template (t5e/template [(opt/fighting-style-selection :fighter)])
        longsword (weapons/weapons-map :longsword)
        without (damage (fighter :defense :longsword :shield) template longsword)]
    (testing "+2 with a one-handed melee weapon and a shield in the off hand"
      (is (= (+ without 2)
             (damage (fighter :dueling :longsword :shield) template longsword))))
    (testing "no bonus with a weapon in the off hand"
      (is (= without
             (damage (fighter :dueling :longsword :dagger) template longsword))))
    (testing "no bonus with a two-handed weapon"
      (let [maul (weapons/weapons-map :maul)]
        (is (= (damage (fighter :defense :maul :shield) template maul)
               (damage (fighter :dueling :maul :shield) template maul)))))))

(deftest dueling-looks-weapons-up-in-the-template
  (let [selections [(opt/fighting-style-selection :fighter)]
        raw (fighter :dueling :test-blade :shield)
        custom (damage raw (t5e/template selections custom-weapons-map) test-blade)
        static (damage raw (t5e/template selections) test-blade)]
    (is (= (+ static 2) custom))))

(deftest dual-wield-ac-looks-weapons-up-in-the-template
  (let [selections [(t/selection-cfg
                     {:name "Feat"
                      :options [(t/option-cfg {:name "Dual Wielder"
                                               :modifiers [opt/dual-wield-ac-mod]})]})]
        raw {::entity/options {:feat {::entity/key :dual-wielder}}
             ::entity/values {::char5e/main-hand-weapon :test-blade
                              ::char5e/off-hand-weapon :test-blade}}
        ac (fn [template]
             ((char5e/armor-class-with-armor (entity/build raw template)) nil nil))]
    (is (= (inc (ac (t5e/template selections)))
           (ac (t5e/template selections custom-weapons-map))))))

(deftest none-option-reads-homebrew-paths-from-the-built-character
  (let [path [:race :custom :subrace]
        prereq-fn (-> (opt/none-option path) ::t/prereqs first ::t/prereq-fn)
        template (t5e/template [])]
    (testing "passes when the path is homebrew"
      (is (prereq-fn (entity/build {::entity/homebrew-paths {path true}} template))))
    (testing "fails otherwise"
      (is (not (prereq-fn (entity/build {} template)))))))
