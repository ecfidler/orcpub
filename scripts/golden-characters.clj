;; Phase A, M0: generate the golden character fixtures (HANDOFF §4.2).
;;
;;   lein run -m clojure.main scripts/golden-characters.clj
;;   scripts/oracle-env.sh scripts/golden-characters.clj      ; no Leiningen/Clojars
;;
;; Every golden character is a raw entity written in code (the warlock_test.clj
;; approach), converted with char5e/to-strict, written to
;; fixtures/characters/<name>.strict.json, read back from that file and built
;; against the old app's template (SRD + the packs the character needs) to
;; produce <name>.expected.json and <name>.selections.json. <name>.meta.json
;; records the packs, a description and the checks that were run.
;; fixtures/legacy/ gets the three real Datomic entities from character_test.clj
;; and one synthetic strict entity per import quirk R2-R9 (doc 01 §C2).
(load-file "scripts/orcpub/oracle.clj")

(ns golden-characters
  (:require [orcpub.oracle :as oracle]
            [orcpub.entity :as entity]
            [orcpub.template :as t]
            [orcpub.dnd.e5.character :as char5e]
            [orcpub.dnd.e5.character.equipment :as equip]
            [orcpub.dnd.e5.spells :as spells5e]
            [orcpub.dnd.e5.weapons :as weapons5e]
            [orcpub.dnd.e5.spell-lists :as sl5e]
            [clojure.string :as str]
            [clojure.java.io :as io]))

(def characters-dir "fixtures/characters")
(def legacy-dir "fixtures/legacy")
(def orcbrew-dir "fixtures/orcbrew")

;;; ---------------------------------------------------------------------------
;;; Entity-building helpers
;;; ---------------------------------------------------------------------------

(defn opt
  ([k] {::entity/key k})
  ([k options] {::entity/key k ::entity/options options}))

(defn val-opt [k v] {::entity/key k ::entity/value v})

(defn item
  "An inventory entry (weapons/armor/equipment/treasure/magic-* selections)."
  [k qty & {:keys [equipped? class? bg?] :or {equipped? true}}]
  {::entity/key k
   ::entity/value (cond-> {::equip/quantity qty ::equip/equipped? equipped?}
                    class? (assoc ::equip/class-starting-equipment? true)
                    bg? (assoc ::equip/background-starting-equipment? true))})

(def die-average {6 4, 8 5, 10 6, 12 7})

(defn class-levels
  "Level options 1..n; every level past 1 takes average hit points (the value
   is what the old UI stores for the Average option). `per-level` adds
   selections to particular levels, e.g. {3 {:martial-archetype (opt :champion)}}.
   A class that is not the character's first class also rolls hit points at
   its level 1 (`hp-at-1?`)."
  [n die & [per-level hp-at-1?]]
  (mapv (fn [l]
          (let [options (merge (when (or (> l 1) hp-at-1?) {:hit-points (val-opt :average (die-average die))})
                               (get per-level l))]
            (if (seq options)
              (opt (keyword (str "level-" l)) options)
              (opt (keyword (str "level-" l))))))
        (range 1 (inc n))))

(defn asi [a b]
  (opt :ability-score-improvement {:asi [(opt a) (opt b)]}))

(def feat-instead (opt :feat))

(def A ::char5e/str) (def D ::char5e/dex) (def C ::char5e/con)
(def I ::char5e/int) (def W ::char5e/wis) (def CH ::char5e/cha)

(defn abilities [s d c i w ch]
  (char5e/abilities s d c i w ch))

(def acolyte
  (opt :acolyte {:starting-equipment-holy-symbol (opt :amulet)
                 :starting-equipment-prayer-book-wheel (opt :prayer-book)}))

;; Inventory entries hold only the fixed starting items, the ones the old UI
;; writes when the background or first class is selected
;; (event_handlers.cljc add-background-starting-equipment, character.cljc
;; set-class). Items from a starting-equipment *choice* (the holy symbol, a
;; pack, the fighter's armor and weapons, the wizard's quarterstaff) come from
;; that option's modifiers (options.cljc starting-equipment-option,
;; equipment-option) and never appear in the inventory (ORC-11).
(def acolyte-items
  [(item :clothes-common 1 :bg? true) (item :pouch 1 :bg? true) (item :incense 5 :bg? true)
   (item :vestements 1 :bg? true)])

;;; wizard spell picking: 6 first-level spells at level 1, then two spells of
;;; the highest castable level at every level after, walking the SRD wizard
;;; list in order.
(def wizard-list (get sl5e/spell-lists :wizard))

(defn wizard-spells-known [class-level]
  (loop [l 1 taken []]
    (if (> l class-level)
      taken
      (let [n (if (= l 1) 6 2)
            spell-level (min 9 (int (Math/ceil (/ l 2))))
            available (fn [lvl] (remove (set taken) (get wizard-list lvl)))
            picks (loop [lvl spell-level picks []]
                    (if (or (= (count picks) n) (< lvl 1))
                      picks
                      (recur (dec lvl) (into picks (take (- n (count picks)) (available lvl))))))]
        (recur (inc l) (into taken picks))))))

(defn wizard-cantrips [class-level]
  (vec (take (cond-> 3 (>= class-level 4) inc (>= class-level 10) inc) (get wizard-list 0))))

(defn wizard-class
  "A wizard class option at `level` with a full spellbook and Evocation from
   level 2. `first-class?` adds the skill and starting-equipment selections
   (only the first class gets them; the old UI hides both for a later class)."
  [level & {:keys [first-class? per-level] :or {first-class? true}}]
  (opt :wizard
       (cond-> {:levels (class-levels level 6
                                      (merge (when (>= level 2) {2 {:arcane-tradition (opt :school-of-evocation)}})
                                             (when (>= level 18) {18 {:spell-mastery-level-1-spell (opt (first (get wizard-list 1)))
                                                                      :spell-mastery-level-2-spell (opt (first (get wizard-list 2)))}})
                                             (when (>= level 20) {20 {:signature-spells (mapv opt (take 2 (get wizard-list 3)))}})
                                             per-level)
                                      (not first-class?))
                :wizard-cantrips-known (mapv opt (wizard-cantrips level))
                :wizard-spells-known (mapv opt (wizard-spells-known level))}
         first-class? (assoc :starting-equipment-melee-weapon (opt :quarterstaff)
                             :starting-equipment-equipment-pack (opt :scholars-pack)
                             :starting-equipment-spellcasting-equipment (opt :component-pouch)
                             :skill-proficiency [(opt :arcana) (opt :investigation)]))))

(defn prepared-wizard-spells [level int-score]
  (let [n (+ level (int (Math/floor (/ (- int-score 10) 2))))]
    {"Wizard" (set (take n (wizard-spells-known level)))}))

;;; ---------------------------------------------------------------------------
;;; The golden characters
;;; ---------------------------------------------------------------------------

(defn fighter-base [level per-level & {:keys [style skills] :or {style :defense skills [:athletics :perception]}}]
  (opt :fighter {:fighting-style [(opt style)]
                 :starting-equipment-armor (opt :chain-mail)
                 :starting-equipment-weapons (opt :martial-weapon-and-shield
                                                  {:starting-equipment-martial-weapon (opt :longsword)})
                 :starting-equipment-additional-weapons (opt :two-handaxes)
                 :starting-equipment-equipment-pack (opt :dungeoneers-pack)
                 :skill-proficiency (mapv opt skills)
                 :levels (class-levels level 10 per-level)}))

;; The fighter has no fixed starting items: its chain mail, longsword, shield,
;; handaxes and dungeoneer's pack all come from fighter-base's choices.
(def fighter-items
  {:equipment acolyte-items
   :treasure [(item :gp 15 :bg? true)]})

(def golden-characters
  [{:name "fighter-1"
    :description "Level 1 human (standard) fighter, Acolyte, Defense style, chain mail + longsword + shield. Baseline martial."
    :raw {::entity/options
          (merge {:ability-scores (val-opt :standard-scores (abilities 15 14 13 12 10 8))
                  :alignment (opt :lawful-good)
                  :race (opt :human {:subrace (opt :damaran) :variant (opt :standard-human)})
                  :languages [(opt :common) (opt :dwarvish) (opt :elvish)]
                  :background acolyte
                  :class [(fighter-base 1 {})]}
                 fighter-items)
          ::entity/values {::char5e/character-name "Brannor Ironfist"
                           ::char5e/xps 0
                           ::char5e/worn-armor :chain-mail
                           ::char5e/wielded-shield :shield
                           ::char5e/main-hand-weapon :longsword
                           ::char5e/off-hand-weapon :shield}}
    :checks (fn [b] [[1 (char5e/total-levels b)] [2 (char5e/proficiency-bonus b)]
                     [12 (char5e/max-hit-points b)] [16 (:orcpub.dnd.e5.character/str (char5e/ability-values b))]])}

   {:name "fighter-5"
    :description "Level 5 hill dwarf fighter (Champion), Dueling style, ASI at 4 (STR+2). Extra Attack; longsword in the main hand and the shield in the off hand, which is what options.cljc's Dueling condition requires (a non-weapon in the off-hand slot) for the +2 damage bonus (patch D2 coverage)."
    :raw {::entity/options
          (merge {:ability-scores (val-opt :standard-roll (abilities 16 12 15 10 13 8))
                  :alignment (opt :lawful-neutral)
                  :race (opt :dwarf {:subrace (opt :hill-dwarf) :tool-proficiency (opt :smiths-tools)})
                  :languages [(opt :giant) (opt :orc)]
                  :background acolyte
                  :class [(fighter-base 5 {3 {:martial-archetype (opt :champion)}
                                           4 {:asi-or-feat (asi A A)}}
                                        :style :dueling :skills [:athletics :intimidation])]}
                 fighter-items)
          ::entity/values {::char5e/character-name "Durga Anvilmar"
                           ::char5e/xps 6500
                           ::char5e/current-hit-points 40
                           ::char5e/worn-armor :chain-mail
                           ::char5e/wielded-shield :shield
                           ::char5e/main-hand-weapon :longsword
                           ::char5e/off-hand-weapon :shield}}
    :checks (fn [b] [[5 (char5e/total-levels b)] [3 (char5e/proficiency-bonus b)] [2 (char5e/number-of-attacks b)]
                     [18 (:orcpub.dnd.e5.character/str (char5e/ability-values b))]
                     [#{19 20} (set (char5e/critical-hit-values b))]
                     ;; Dueling: +2 damage with the one-handed longsword, none with the two-handed greataxe
                     [6 (char5e/weapon-damage-modifier b (:longsword orcpub.dnd.e5.weapons/weapons-map) false)]
                     [4 (char5e/weapon-damage-modifier b (:greataxe orcpub.dnd.e5.weapons/weapons-map) false)]])}

   {:name "fighter-11"
    :description "Level 11 half-orc fighter (Champion), Great Weapon Fighting, three attacks, ASIs at 4/6/8; greataxe main hand."
    :raw {::entity/options
          (merge {:ability-scores (val-opt :point-buy (abilities 15 13 14 8 12 10))
                  :alignment (opt :chaotic-good)
                  :race (opt :half-orc)
                  :languages [(opt :dwarvish) (opt :goblin)]
                  :background acolyte
                  :class [(fighter-base 11 {3 {:martial-archetype (opt :champion)}
                                            4 {:asi-or-feat (asi A A)}
                                            6 {:asi-or-feat (asi C C)}
                                            8 {:asi-or-feat (asi A D)}}
                                        :style :great-weapon-fighting :skills [:athletics :survival])]}
                 (assoc fighter-items :weapons [(item :greataxe 1)]))
          ::entity/values {::char5e/character-name "Ghorbash"
                           ::char5e/xps 85000
                           ::char5e/worn-armor :chain-mail
                           ::char5e/main-hand-weapon :greataxe}}
    :checks (fn [b] [[11 (char5e/total-levels b)] [4 (char5e/proficiency-bonus b)] [3 (char5e/number-of-attacks b)]])}

   {:name "fighter-20"
    :description "Level 20 human (standard) fighter (Champion), Protection style, four attacks, every ASI taken (one as the Grappler feat), plate armor and a +1 longsword equipped, an attuned amulet of health."
    :raw {::entity/options
          (merge {:ability-scores (val-opt :standard-scores (abilities 15 14 13 12 10 8))
                  :alignment (opt :neutral-good)
                  :race (opt :human {:subrace (opt :illuskan) :variant (opt :standard-human)})
                  :languages [(opt :common) (opt :elvish) (opt :draconic)]
                  :background acolyte
                  :feats [(opt :grappler)]
                  :class [(fighter-base 20 {3 {:martial-archetype (opt :champion)}
                                            4 {:asi-or-feat (asi A A)}
                                            6 {:asi-or-feat feat-instead}
                                            8 {:asi-or-feat (asi A A)}
                                            12 {:asi-or-feat (asi C C)}
                                            14 {:asi-or-feat (asi D D)}
                                            16 {:asi-or-feat (asi C W)}
                                            19 {:asi-or-feat (asi W CH)}}
                                        :style :protection :skills [:athletics :perception])]}
                 (assoc fighter-items
                        :armor [(item :plate 1)]
                        :magic-weapons [(item :longsword-1 1)]
                        :other-magic-items [(item :amulet-of-health 1)]))
          ::entity/values {::char5e/character-name "Ser Aldric"
                           ::char5e/xps 355000
                           ::char5e/worn-armor :plate
                           ::char5e/wielded-shield :shield
                           ::char5e/main-hand-weapon :longsword-1
                           ::char5e/attuned-magic-items [:amulet-of-health]}}
    :checks (fn [b] [[20 (char5e/total-levels b)] [6 (char5e/proficiency-bonus b)] [4 (char5e/number-of-attacks b)]])}

   {:name "wizard-1"
    :description "Level 1 high elf wizard, Acolyte, three cantrips + the High Elf cantrip, six spells known, four prepared."
    :raw {::entity/options
          {:ability-scores (val-opt :standard-scores (abilities 8 14 13 15 12 10))
           :alignment (opt :neutral)
           :race (opt :elf {:subrace (opt :high-elf {:high-elf-cantrips-known (opt :minor-illusion)})})
           :languages [(opt :draconic) (opt :sylvan) (opt :gnomish)]
           :background acolyte
           :class [(wizard-class 1)]
           :equipment (into [(item :spellbook 1 :class? true)] acolyte-items)
           :treasure [(item :gp 15 :bg? true)]}
          ::entity/values {::char5e/character-name "Ilyana Starweave"
                           ::char5e/xps 0
                           ::char5e/prepared-spells-by-class (prepared-wizard-spells 1 16)
                           ::char5e/main-hand-weapon :quarterstaff}}
    :checks (fn [b] [[1 (char5e/total-levels b)] [7 (char5e/max-hit-points b)]
                     [16 (:orcpub.dnd.e5.character/int (char5e/ability-values b))]
                     [{1 2} (char5e/spell-slots b)]])}

   {:name "wizard-5"
    :description "Level 5 rock gnome wizard (Evocation), ASI at 4 (INT+2), 3rd-level slots, fourteen spells known."
    :raw {::entity/options
          {:ability-scores (val-opt :point-buy (abilities 8 14 14 15 12 10))
           :alignment (opt :chaotic-good)
           :race (opt :gnome {:subrace (opt :rock-gnome)})
           :languages [(opt :dwarvish) (opt :elvish)]
           :background acolyte
           :class [(wizard-class 5 :per-level {4 {:asi-or-feat (asi I I)}})]
           :weapons [(item :dagger 2)]
           :equipment (into [(item :spellbook 1 :class? true)] acolyte-items)
           :treasure [(item :gp 40)]}
          ::entity/values {::char5e/character-name "Fimble Nackle"
                           ::char5e/xps 6500
                           ::char5e/prepared-spells-by-class (prepared-wizard-spells 5 19)
                           ::char5e/main-hand-weapon :dagger}}
    :checks (fn [b] [[5 (char5e/total-levels b)] [{1 4 2 3 3 2} (char5e/spell-slots b)]
                     [19 (:orcpub.dnd.e5.character/int (char5e/ability-values b))]])}

   {:name "wizard-11"
    :description "Level 11 tiefling wizard (Evocation), ASIs at 4 and 8, 6th-level slots, five cantrips."
    :raw {::entity/options
          {:ability-scores (val-opt :standard-roll (abilities 9 13 14 17 11 14))
           :alignment (opt :lawful-neutral)
           :race (opt :tiefling)
           :languages [(opt :abyssal) (opt :celestial)]
           :background acolyte
           :class [(wizard-class 11 :per-level {4 {:asi-or-feat (asi I I)} 8 {:asi-or-feat (asi I C)}})]
           :armor []
           :equipment (into [(item :spellbook 1 :class? true)] acolyte-items)
           :other-magic-items [(item :cloak-of-protection 1)]
           :treasure [(item :gp 120) (item :pp 4)]}
          ::entity/values {::char5e/character-name "Zariel Ashenwright"
                           ::char5e/xps 85000
                           ::char5e/prepared-spells-by-class (prepared-wizard-spells 11 20)
                           ::char5e/attuned-magic-items [:cloak-of-protection]}}
    :checks (fn [b] [[11 (char5e/total-levels b)] [{1 4 2 3 3 3 4 3 5 2 6 1} (char5e/spell-slots b)]
                     [5 (count (filter #(= "Wizard" (:class %)) (vals (get (char5e/spells-known b) 0))))]
                     [6 (count (get (char5e/spells-known b) 0))]])}

   {:name "wizard-20"
    :description "Level 20 human (standard) wizard (Evocation), every ASI, 9th-level slots, forty-four spells known, Spell Mastery and Signature Spells levels present."
    :raw {::entity/options
          {:ability-scores (val-opt :standard-scores (abilities 8 14 13 15 12 10))
           :alignment (opt :neutral-good)
           :race (opt :human {:subrace (opt :chondathan) :variant (opt :standard-human)})
           :languages [(opt :common) (opt :draconic) (opt :primordial)]
           :background acolyte
           :class [(wizard-class 20 :per-level {4 {:asi-or-feat (asi I I)} 8 {:asi-or-feat (asi I I)}
                                                12 {:asi-or-feat (asi C C)} 16 {:asi-or-feat (asi D W)}
                                                19 {:asi-or-feat (asi W CH)}})]
           :equipment (into [(item :spellbook 1 :class? true)] acolyte-items)
           :treasure [(item :gp 1500)]}
          ::entity/values {::char5e/character-name "Archmage Veyra"
                           ::char5e/xps 355000
                           ::char5e/prepared-spells-by-class (prepared-wizard-spells 20 20)}}
    :checks (fn [b] [[20 (char5e/total-levels b)] [6 (char5e/proficiency-bonus b)]
                     [{1 4 2 3 3 3 4 3 5 3 6 2 7 2 8 1 9 1} (char5e/spell-slots b)]])}

   {:name "fighter-3-wizard-2"
    :description "Multiclass: half-elf fighter 3 (Champion, Defense) / wizard 2 (Evocation). Wizard is the second class, so no wizard skill selection; spell slots from two wizard levels."
    :raw {::entity/options
          (merge {:ability-scores (val-opt :standard-scores (abilities 15 13 14 14 10 8))
                  :alignment (opt :chaotic-neutral)
                  :race (opt :half-elf {:asi [(opt A) (opt I)] :skill-proficiency [(opt :deception) (opt :persuasion)]})
                  :languages [(opt :common) (opt :elvish) (opt :dwarvish) (opt :orc)]
                  :background acolyte
                  :class [(fighter-base 3 {3 {:martial-archetype (opt :champion)}})
                          (wizard-class 2 :first-class? false)]}
                 fighter-items)
          ::entity/values {::char5e/character-name "Corvin Half-Elven"
                           ::char5e/xps 6500
                           ::char5e/prepared-spells-by-class (prepared-wizard-spells 2 15)
                           ::char5e/worn-armor :chain-mail
                           ::char5e/wielded-shield :shield
                           ::char5e/main-hand-weapon :longsword}}
    :checks (fn [b] [[5 (char5e/total-levels b)] [3 (char5e/proficiency-bonus b)]
                     [{:fighter 3 :wizard 2} (into {} (map (fn [[k v]] [k (:class-level v)])) (char5e/levels b))]
                     [{1 3} (char5e/spell-slots b)]])}

   {:name "warlock-10-drow"
    :description "The warlock_test.clj entity: level 10 Dark Elf (Drow) warlock of the Archfey, Spy background, Keen Mind feat, Pact of the Tome, five invocations. Needs warlock-test-content.orcbrew for the Drow subrace, Spy and Keen Mind."
    :orcbrew ["warlock-test-content.orcbrew"]
    :raw (do (require 'orcpub.dnd.e5.warlock-test)
             @(resolve 'orcpub.dnd.e5.warlock-test/warlock-entity))
    :checks (fn [b] [[10 (char5e/total-levels b)]
                     [{A 10 D 13 C 11 I 16 W 14 CH 16} (char5e/ability-values b)]
                     ["Elf" (char5e/race b)] ["Dark Elf (Drow)" (char5e/subrace b)]
                     [5 (count (char5e/skill-proficiencies b))]
                     [true (boolean (get-in (char5e/spells-known b) [1 ["Warlock" :illusory-script]]))]
                     [true (boolean (get-in (char5e/spells-known b) [0 ["Warlock" :spare-the-dying]]))]
                     [true (boolean (get-in (char5e/spells-known b) [1 ["Warlock" :speak-with-animals]]))]
                     [30 (char5e/base-land-speed b)]])}

   {:name "ironwrought-artificer-3"
    :description "Homebrew-only character: Ironwrought (Envoy) race and Artificer (Alternate) class with the Alchemist subclass, all from duplicate-external-b.orcbrew."
    :orcbrew ["duplicate-external-b.orcbrew"]
    :raw {::entity/options
          {:ability-scores (val-opt :standard-scores (abilities 10 14 15 13 12 8))
           :alignment (opt :lawful-good)
           :race (opt :ironwrought {:subrace (opt :envoy)})
           :languages [(opt :common) (opt :gnomish)]
           :background acolyte
           :class [(opt :artificer {:levels (class-levels 3 8 {3 {:artificer-specialization (opt :alchemist)}})})]
           :weapons [(item :light-hammer 1)]
           :armor [(item :scale-mail 1)]
           :equipment acolyte-items}
          ::entity/values {::char5e/character-name "Unit Seven"
                           ::char5e/xps 900
                           ::char5e/worn-armor :scale-mail}}
    :checks (fn [b] [[3 (char5e/total-levels b)] ["Ironwrought" (char5e/race b)] ["Envoy" (char5e/subrace b)]
                     ;; the pack's :abilities {:con 2} uses an unqualified key (drift
                     ;; form 10); the old engine keeps it verbatim, so CON stays 15
                     [{:con 2} (char5e/race-ability-increases b)]
                     [15 (:orcpub.dnd.e5.character/con (char5e/ability-values b))]])}])

;;; ---------------------------------------------------------------------------
;;; Legacy fixtures
;;; ---------------------------------------------------------------------------

(defn character-test-strict
  "The strict entity bound to `strict` in a character_test.clj deftest."
  [test-name]
  (require 'orcpub.dnd.e5.character-test)
  (binding [*ns* (find-ns 'orcpub.dnd.e5.character-test)]
    (with-open [r (java.io.PushbackReader. (io/reader "test/cljc/orcpub/dnd/e5/character_test.clj"))]
      (loop []
        (let [f (read {:eof ::eof} r)]
          (cond (= f ::eof) (throw (ex-info (str "no deftest " test-name) {}))
                (and (seq? f) (= 'deftest (first f)) (= test-name (second f))) (-> f (nth 2) second second)
                :else (recur)))))))

(defn fighter-5-strict []
  (char5e/to-strict (:raw (first (filter #(= "fighter-5" (:name %)) golden-characters)))))

(defn wizard-5-strict []
  (char5e/to-strict (:raw (first (filter #(= "wizard-5" (:name %)) golden-characters)))))

(defn update-selection
  "Update the strict selection with key k (top-level) via f. Throws if the
   entity has no such selection, so a quirk cannot silently go missing."
  [strict k f]
  (when-not (some #(= k (:orcpub.entity.strict/key %)) (:orcpub.entity.strict/selections strict))
    (throw (ex-info (str "no top-level selection " k) {:key k})))
  (update strict :orcpub.entity.strict/selections
          (fn [sels] (mapv #(if (= k (:orcpub.entity.strict/key %)) (f %) %) sels))))

(def legacy-fixtures
  [{:name "character-test-1"
    :description "Real Datomic entity from character_test.clj strict-round-trip: level 1 barbarian with class starting equipment; no race, background or alignment."
    :strict (character-test-strict 'strict-round-trip)}
   {:name "character-test-2"
    :description "Real Datomic entity from character_test.clj strict-round-trip-2: level 8 human (Damaran) fighter with Eldritch Knight (non-SRD, unresolved), Noble background (non-SRD, unresolved), Ritual Caster feat (unresolved), rolled hit points, magic items, custom equipment, an owner. Exercises R8 (unresolved keys) with real data."
    :strict (character-test-strict 'strict-round-trip-2)}
   {:name "character-test-3"
    :description "Real Datomic entity from character_test.clj strict-round-trip-3: warlock 1 / druid 1 multiclass with Spy background (unresolved without the warlock pack) and background starting equipment."
    :strict (character-test-strict 'strict-round-trip-3)}
   {:name "r3-slots-used-vectors"
    :quirk "R3"
    :description "slots-used values stored as vectors instead of sets, and a stray :db/id inside features-used (update-values-from-strict handles both)."
    :strict (-> (wizard-5-strict)
                (assoc-in [:orcpub.entity.strict/values :orcpub.dnd.e5.spells/slots-used] {1 [1 3] 2 [2]})
                (assoc-in [:orcpub.entity.strict/values ::char5e/features-used] {:db/id 17592186099999 :arcane-recovery [1]}))}
   {:name "r4-zero-int-value"
    :quirk "R4"
    :description "A rolled hit-point option with int-value 0 and a string-value \"\" as the character name. Doc 01 R4 says these read back as nil; they do not: (or 0 nil nil) is 0 in Clojure and ClojureScript alike, so the roll counts as 0 hit points and the name stays \"\" (see fixtures/README.md, findings)."
    :strict (-> (fighter-5-strict)
                (update-selection :class
                                  (fn [class-sel]
                                    (update-in class-sel [:orcpub.entity.strict/options 0 :orcpub.entity.strict/selections]
                                               (fn [sels]
                                                 (mapv (fn [s]
                                                         (if (= :levels (:orcpub.entity.strict/key s))
                                                           (update-in s [:orcpub.entity.strict/options 1 :orcpub.entity.strict/selections 0 :orcpub.entity.strict/option]
                                                                      (fn [o] (-> o (dissoc :orcpub.entity.strict/map-value)
                                                                                  (assoc :orcpub.entity.strict/key :roll
                                                                                         :orcpub.entity.strict/int-value 0))))
                                                           s))
                                                       sels)))))
                (assoc-in [:orcpub.entity.strict/values ::char5e/character-name] ""))}
   {:name "r5-xps-string"
    :quirk "R5"
    :description "xps stored as the string \"6500\" (the old server coerced it in routes.clj; the new importer must parse it, blank/invalid → 0)."
    :strict (assoc-in (fighter-5-strict) [:orcpub.entity.strict/values ::char5e/xps] "6500")}
   {:name "r5-xps-blank-string"
    :quirk "R5"
    :description "xps stored as a blank string; imports as 0."
    :strict (assoc-in (fighter-5-strict) [:orcpub.entity.strict/values ::char5e/xps] " ")}
   {:name "r6-quantity-string"
    :quirk "R6"
    :description "An equipment quantity stored as the string \"5\" (fix-quantities parses it on save; the importer applies it on import too)."
    :strict (update-selection (fighter-5-strict) :equipment
                              (fn [sel] (update sel :orcpub.entity.strict/options
                                                (fn [opts] (mapv #(if (= :incense (:orcpub.entity.strict/key %))
                                                                    (assoc-in % [:orcpub.entity.strict/map-value ::equip/quantity] "5")
                                                                    %)
                                                                 opts)))))}
   {:name "r7-unqualified-keys"
    :quirk "R7"
    :description "Legacy unqualified keys: ability scores as :str/:dex/..., equipment map-values as :quantity/:equipped?, values as :character-name/:xps. Not inherited — the importer must namespace them (patch D1)."
    :strict (-> (fighter-5-strict)
                (update-selection :ability-scores
                                  (fn [sel] (assoc-in sel [:orcpub.entity.strict/option :orcpub.entity.strict/map-value]
                                                      {:str 16 :dex 12 :con 15 :int 10 :wis 13 :cha 8})))
                (update-selection :equipment
                                  (fn [sel] (update sel :orcpub.entity.strict/options
                                                    (fn [opts] (mapv #(assoc % :orcpub.entity.strict/map-value
                                                                            {:quantity (get-in % [:orcpub.entity.strict/map-value ::equip/quantity])
                                                                             :equipped? true
                                                                             :background-starting-equipment? true})
                                                                     opts)))))
                (assoc :orcpub.entity.strict/values {:character-name "Durga Anvilmar" :xps 6500
                                                     :worn-armor :chain-mail :wielded-shield :shield
                                                     :main-hand-weapon :longsword
                                                     :custom-equipment [{:name "Clan signet" :quantity 1 :equipped? true}]}))}
   {:name "r8-unresolved-keys"
    :quirk "R8"
    :description "The ironwrought-artificer-3 character without its pack: race, subrace, class and subclass keys do not resolve. Built against SRD only; content_reconciliation is the new app's job."
    :strict (char5e/to-strict (:raw (first (filter #(= "ironwrought-artificer-3" (:name %)) golden-characters))))}
   {:name "r9-duplicate-options"
    :quirk "R9"
    :description "A multi-select option key repeated (magic-missile twice in wizard-spells-known); has-duplicate-selections? flags it, the build tolerates it."
    :strict (update-selection (wizard-5-strict) :class
                              (fn [class-sel]
                                (update-in class-sel [:orcpub.entity.strict/options 0 :orcpub.entity.strict/selections]
                                           (fn [sels]
                                             (mapv (fn [s]
                                                     (if (= :wizard-spells-known (:orcpub.entity.strict/key s))
                                                       (update s :orcpub.entity.strict/options conj {:orcpub.entity.strict/key :magic-missile})
                                                       s))
                                                   sels)))))}])

;;; ---------------------------------------------------------------------------
;;; Writing
;;; ---------------------------------------------------------------------------

(def template-cache (atom {}))

(defn template-for [orcbrew-files]
  (or (get @template-cache orcbrew-files)
      (let [plugins (oracle/plugins-for-orcbrew-files (map #(str orcbrew-dir "/" %) orcbrew-files))
            template (oracle/template-for-plugins plugins)]
        (swap! template-cache assoc orcbrew-files template)
        template)))

(defn run-checks [checks built]
  (doall
   (for [[expected actual] (checks built)]
     (do (when (not= expected actual)
           (println "  CHECK FAILED: expected" (pr-str expected) "got" (pr-str actual)))
         {"expected" (oracle/->plain expected) "actual" (oracle/->plain actual) "pass" (= expected actual)}))))

(defn write-fixture!
  [dir {:keys [name description quirk orcbrew raw strict checks] :or {orcbrew []}}]
  (println "==" name)
  (let [strict (or strict (char5e/to-strict raw))
        strict-path (str dir "/" name ".strict.json")
        _ (oracle/write-strict-file strict-path strict)
        strict* (oracle/read-strict-file strict-path)
        _ (when (not= strict strict*) (println "  WARNING: strict entity did not survive the Transit round trip"))
        round-trip (try (if (= strict (char5e/to-strict (char5e/from-strict strict))) true false)
                        (catch Exception e (str "throws: " (.getMessage e))))
        template (template-for orcbrew)
        {:keys [raw built]} (oracle/build-strict strict* template)
        expected (oracle/expected-values built)
        selections (oracle/selections-summary raw built template)
        unfilled (filter #(pos? (get % "remaining")) selections)
        check-results (when checks (run-checks checks built))]
    (doseq [u unfilled]
      (println "  unfilled:" (get u "actualPath") "remaining" (get u "remaining")))
    (oracle/write-json-file (str dir "/" name ".expected.json") expected)
    (oracle/write-json-file (str dir "/" name ".selections.json") selections)
    (oracle/write-json-file (str dir "/" name ".meta.json")
                            (cond-> {"name" name
                                     "description" description
                                     "orcbrew" (vec orcbrew)
                                     "strictRoundTrip" round-trip
                                     "unfilledSelections" (mapv #(get % "actualPath") unfilled)
                                     "checks" (or check-results [])}
                              quirk (assoc "quirk" quirk)))
    (println (format "  levels=%s hp=%s ac=%s unfilled=%d checks=%s"
                     (pr-str (into {} (map (fn [[k v]] [k (:class-level v)])) (char5e/levels built)))
                     (char5e/max-hit-points built)
                     (get-in expected ["armor-class-with-armor" 0 "ac"])
                     (count unfilled)
                     (if checks (str (count (filter #(get % "pass") check-results)) "/" (count check-results)) "-")))))

(defn -main [& _]
  (doseq [c golden-characters] (write-fixture! characters-dir c))
  (doseq [l legacy-fixtures] (write-fixture! legacy-dir l))
  (println "done"))

;; Regenerates on load: (load-file "scripts/golden-characters.clj") in a REPL
;; does the same as running the script.
(-main)
