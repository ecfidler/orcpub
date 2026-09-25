(ns orcpub.facade.template
  "The old app's homebrew -> template subscription chain as plain functions
  (ORC-16, docs/ts-rewrite-plan/02-engine-library.md §De-re-framing).

  The old app computes the character template through re-frame
  subscriptions rooted at `::e5/plugins` (app-db `:plugins`) and
  `::mi5e/custom-items`. Each private function below mirrors one of those
  subscriptions: its body is the handler body copied verbatim, and only the
  parameter list changes (multi-input subscriptions take their inputs as
  positional arguments, and the unused query vector is dropped). `build`
  composes them in the order re-frame would, computing each input once.

  State mirrored: app-db `:plugins` = the `plugins` argument, no logged-in
  user, so `::mi5e/custom-items` = [] (`equipment_subs.cljs:48-50`). This is
  what the M0 oracle (`scripts/orcpub/oracle.clj`, `load-old-subs!`) uses.

  `orcpub.dnd.e5.equipment-subs` cannot be required (it reads `js/window` at
  load), so the non-subscription helpers it defines are copied here and
  marked COPIED. Helpers from `orcpub.dnd.e5.spell-subs` are referenced, not
  copied; they are referred by name so that the copied handler bodies stay
  textually identical to the originals."
  (:require [orcpub.common :as common]
            [orcpub.template :as t]
            [orcpub.dnd.e5 :as e5]
            [orcpub.dnd.e5.backgrounds :as bg5e]
            [orcpub.dnd.e5.races :as races5e]
            [orcpub.dnd.e5.classes :as classes5e]
            [orcpub.dnd.e5.feats :as feats5e]
            [orcpub.dnd.e5.modifiers :as mod5e]
            [orcpub.dnd.e5.options :as opt5e]
            [orcpub.dnd.e5.magic-items :as mi5e]
            [orcpub.dnd.e5.weapons :as weapon5e]
            [orcpub.dnd.e5.armor :as armor5e]
            [orcpub.dnd.e5.spells :as spells5e]
            [orcpub.dnd.e5.spell-lists :as sl5e]
            [orcpub.dnd.e5.template :as t5e]
            [orcpub.dnd.e5.spell-subs
             :refer [acolyte-bg
                     languages
                     spell-modifiers
                     make-levels
                     compare-keys
                     base-class-options
                     merge-spell-lists
                     dwarf-option-cfg
                     elf-option-cfg
                     halfling-option-cfg
                     human-option-cfg
                     dragonborn-option-cfg
                     gnome-option-cfg
                     half-elf-option-cfg
                     half-orc-option-cfg
                     tiefling-option-cfg]]))

;;; ---------------------------------------------------------------------------
;;; Helpers COPIED from equipment_subs.cljs (not subscriptions)
;;; ---------------------------------------------------------------------------

;; COPIED: equipment_subs.cljs:29-31 (`sorted-items`, a def, not the
;; ::char5e/sorted-items subscription).
(def ^:private sorted-items
  (delay (sort-by mi5e/name-key mi5e/magic-items))
  )

;; COPIED: equipment_subs.cljs:126-133
(defn- map-by-key-or-id [items]
  (reduce
   (fn [m {:keys [:db/id key] :as item}]
     (assoc m
            key item
            id item))
   {}
   items))

;; COPIED: equipment_subs.cljs:141-164. Returns a handler of
;; [items query-vec], so callers below pass nil for the query vector.
(defn- magic-item-options [modifier-fn nm]
  (fn [items _]
    (map
     (fn [{:keys [:db/id
                  ::mi5e/name
                  key
                  ::mi5e/description
                  ::mi5e/page
                  ::mi5e/modifiers
                  ::mi5e/source] :as item}]
       (let [item-key (or key (keyword (str "id-" id)))
             full-item (update item
                               ::mi5e/modifiers
                               mod5e/build-modifiers)]
         (t/option-cfg
          {:name (or (:name item) name)
           :key item-key
           :help (when (or description
                         page)
                   (t5e/inventory-help description page source))
           :modifiers [(modifier-fn
                        item-key
                        full-item)]})))
     items)))

;;; ---------------------------------------------------------------------------
;;; Roots
;;; ---------------------------------------------------------------------------

;; ::e5/plugins — spell_subs.cljs:38-41
(defn- plugins-sub [db]
  (get db :plugins))

;; ::mi5e/custom-items — equipment_subs.cljs:48-50, the branch the old app
;; registers when there is no js/window.location. The browser branch
;; (:33-47) fetches a logged-in user's items over HTTP and reads app-db
;; ::mi5e/custom-items, which is absent (so []) with no user logged in.
(defn- custom-items []
  [])

;;; ---------------------------------------------------------------------------
;;; spell_subs.cljs
;;; ---------------------------------------------------------------------------

;; ::e5/plugin-vals — spell_subs.cljs:43-75
(defn- plugin-vals [plugins]
  ;; Defensive handling: filter out malformed plugin data to prevent
  ;; subscription chain failures that can break the class dropdown
  (let [result (keep
                (fn [p]
                  (try
                    (when (map? p)
                      (into
                       {}
                       (keep
                        (fn [[type-k type-m]]
                          (when (and type-k (or (nil? type-m) (map? type-m)))
                            [type-k
                             (if (map? type-m)
                               (into
                                {}
                                (keep
                                 (fn [[k v]]
                                   ;; Only include if v is a map and not disabled
                                   (when (and (map? v) (not (:disabled? v)))
                                     [k v]))
                                 type-m))
                               type-m)]))
                        p)))
                    (catch js/Error e
                      (js/console.warn "Skipping malformed plugin data:" (pr-str p) e)
                      nil)))
                (filter (fn [p] (and (map? p) (not (:disabled? p))))
                        (vals plugins)))]
    result))

;; ::e5/plugins-with-sources — spell_subs.cljs:79-88
(defn- plugins-with-sources [plugins]
  ;; Returns seq of [source-name plugin-data] pairs
  (keep
   (fn [[source-name plugin-data]]
     (when (and (map? plugin-data) (not (:disabled? plugin-data)))
       [source-name plugin-data]))
   plugins))

;; ::bg5e/plugin-backgrounds — spell_subs.cljs:90-97
(defn- plugin-backgrounds [plugins]
  (map
   (fn [background]
     (assoc background :edit-event [::bg5e/edit-background background]))
   (mapcat (comp vals ::e5/backgrounds) plugins)))

;; ::langs5e/plugin-languages — spell_subs.cljs:99-103
(defn- plugin-languages [plugins]
  (mapcat (comp vals ::e5/languages) plugins))

;; ::selections5e/plugin-selections — spell_subs.cljs:105-109
(defn- plugin-selections [plugins]
  (mapcat (comp vals ::e5/selections) plugins))

;; ::selections5e/selection-map — spell_subs.cljs:111-115
(defn- selection-map [selections]
  (common/map-by-key selections))

;; ::races5e/plugin-races — spell_subs.cljs:129-141
(defn- plugin-races [plugins]
  (map
   (fn [race]
     (assoc race
            :modifiers
            (concat (opt5e/plugin-modifiers (:props race)
                                            (:key race))
                    (spell-modifiers race (:name race)))
            :edit-event [::races5e/edit-race race]))
   (mapcat (comp vals ::e5/races) plugins)))

;; ::races5e/plugin-subraces — spell_subs.cljs:143-154
(defn- plugin-subraces [plugins]
  (map
   (fn [subrace]
     (assoc subrace
            :modifiers (concat (opt5e/plugin-modifiers (:props subrace)
                                                       (:key subrace))
                               (spell-modifiers subrace (:name subrace)))
            :edit-event [::races5e/edit-subrace subrace]))
   (mapcat (comp vals ::e5/subraces) plugins)))

;; ::classes5e/plugin-subclasses — spell_subs.cljs:425-452
(defn- plugin-subclasses [plugins-with-sources spell-lists spells-map selection-map]
  (keep
   (fn [[source-name subclass-key subclass]]
     (try
       (when (and (map? subclass) subclass-key)
         ;; Ensure the subclass has its key set (the map key is authoritative)
         (let [subclass-with-key (assoc subclass :key subclass-key)
               levels (make-levels spell-lists spells-map selection-map subclass-with-key)]
           (assoc subclass-with-key
                  :modifiers (opt5e/plugin-modifiers (:props subclass)
                                                     subclass-key)
                  :levels levels
                  :plugin-source source-name
                  :edit-event [::classes5e/edit-subclass subclass-with-key])))
       (catch js/Error e
         (js/console.warn "Skipping malformed subclass:" subclass-key e)
         nil)))
   ;; Extract subclasses from each plugin with the map key
   (for [[source-name plugin-data] plugins-with-sources
         [subclass-key subclass-data] (::e5/subclasses plugin-data)
         :when (and (map? subclass-data) (not (:disabled? subclass-data)))]
     [source-name subclass-key subclass-data])))

;; ::classes5e/plugin-classes — spell_subs.cljs:454-494
(defn- plugin-classes [plugins-with-sources spell-lists spells-map selection-map]
  ;; Defensive handling: skip malformed classes rather than breaking
  ;; Also includes source name for disambiguation when multiple sources
  ;; have classes with the same name (e.g., two different "Artificer" classes)
  (keep
   (fn [[source-name class-key class]]
     (try
       (when (and (map? class) class-key)
         (let [;; Ensure the class has its key set (the map key is the authoritative key)
               class-with-key (assoc class :key class-key)
               levels (make-levels spell-lists spells-map selection-map class-with-key)
               ;; Add source name to class name for disambiguation
               ;; Only if source name is meaningful (not default)
               display-name (if (and source-name
                                     (not= source-name "Default Option Source"))
                              (str (:name class) " (" source-name ")")
                              (:name class))]
           (assoc class-with-key
                  :name display-name
                  ;; :name is display-only (may include source suffix).
                  ;; All internal lookups use :key, never :name.
                  :original-name (:name class)
                  :plugin-source source-name
                  :modifiers (opt5e/plugin-modifiers (:props class)
                                                     class-key)
                  :levels levels)))
       (catch js/Error e
         (js/console.warn "Skipping malformed class:" class-key e)
         nil)))
   ;; Extract classes from each plugin with their source name AND the map key
   ;; The map key (e.g., :artificer-kibbles-tasty) is the authoritative key
   (for [[source-name plugin-data] plugins-with-sources
         [class-key class-data] (::e5/classes plugin-data)
         :when (and (map? class-data) (not (:disabled? class-data)))]
     [source-name class-key class-data])))

;; ::feats5e/plugin-feats — spell_subs.cljs:496-500
(defn- plugin-feats [plugins]
  (mapcat (comp vals ::e5/feats) plugins))

;; ::classes5e/plugin-invocations — spell_subs.cljs:502-506
(defn- plugin-invocations [plugins]
  (mapcat (comp vals ::e5/invocations) plugins))

;; ::classes5e/plugin-boons — spell_subs.cljs:508-512
(defn- plugin-boons [plugins]
  (mapcat #(-> % ::e5/boons vals) plugins))

;; ::bg5e/backgrounds — spell_subs.cljs:538-544
(defn- backgrounds [plugin-backgrounds]
  (cons
   acolyte-bg
   plugin-backgrounds))

;; ::langs5e/languages — spell_subs.cljs:580-586. Named languages-sub
;; because the body refers to spell-subs/languages (the built-in list).
(defn- languages-sub [plugin-languages]
  (concat
   languages
   plugin-languages))

;; ::langs5e/language-map — spell_subs.cljs:588-592
(defn- language-map [languages]
  (common/map-by-key languages))

;; ::races5e/plugin-subraces-map — spell_subs.cljs:887-891
(defn- plugin-subraces-map [plugin-subraces]
  (group-by :race plugin-subraces))

;; ::classes5e/plugin-subclasses-map — spell_subs.cljs:893-897
(defn- plugin-subclasses-map [plugin-subclasses]
  (group-by :class plugin-subclasses))

;; ::races5e/races — spell_subs.cljs:902-928
(defn- races [plugin-races subraces-map spell-lists spells-map language-map]
  (vec
   (into
    (sorted-set-by compare-keys)
    (map
     (fn [{:keys [key] :as race}]
       (if (subraces-map key)
         (update race :subraces concat (subraces-map key))
         race))
     (concat
      (reverse plugin-races)
      [dwarf-option-cfg
       (elf-option-cfg spell-lists spells-map language-map)
       halfling-option-cfg
       (human-option-cfg spell-lists spells-map language-map)
       dragonborn-option-cfg
       gnome-option-cfg
       (half-elf-option-cfg language-map)
       half-orc-option-cfg
       tiefling-option-cfg])))))

;; ::classes5e/classes — spell_subs.cljs:945-979
(defn- classes [spell-lists spells-map plugin-subclasses-map language-map plugin-classes invocations boons weapons-map]
  ;; Defensive handling: ensure base classes always render even if plugin classes fail
  (let [base-classes (try
                       (base-class-options spell-lists spells-map plugin-subclasses-map language-map weapons-map invocations boons)
                       (catch js/Error e
                         (js/console.error "Failed to build base classes:" e)
                         []))
        plugin-class-options (keep
                              (fn [plugin-class]
                                (try
                                  (opt5e/class-option
                                   spell-lists
                                   spells-map
                                   plugin-subclasses-map
                                   language-map
                                   weapons-map
                                   plugin-class)
                                  (catch js/Error e
                                    (js/console.warn "Skipping plugin class due to error:" (:key plugin-class) e)
                                    nil)))
                              plugin-classes)]
    (vec
     (into
      (sorted-set-by #(compare (::t/key %1) (::t/key %2)))
      (concat (reverse plugin-class-options) base-classes)))))

;; ::feats5e/feats — spell_subs.cljs:1005-1012
(defn- feats [plugin-feats]
  (map
   (fn [feat]
     (assoc feat :edit-event [::feats5e/edit-feat feat]))
   plugin-feats))

;; ::classes5e/invocations — spell_subs.cljs:1014-1021
(defn- invocations [plugin-invocations]
  (map
   (fn [invocation]
     (assoc invocation :edit-event [::classes5e/edit-invocation invocation]))
   plugin-invocations))

;; ::classes5e/boons — spell_subs.cljs:1023-1030
(defn- boons [plugin-boons]
  (map
   (fn [boon]
     (assoc boon :edit-event [::classes5e/edit-boon boon]))
   plugin-boons))

;; ::spells5e/plugin-spells — spell_subs.cljs:1032-1039
(defn- plugin-spells [plugins]
  (map
   (fn [spell]
     (assoc spell :edit-event [::spells5e/edit-spell spell]))
   (mapcat (comp vals ::e5/spells) plugins)))

;; ::spells5e/spells — spell_subs.cljs:1185-1193
(defn- spells [plugin-spells]
  (into
   (sorted-set-by compare-keys)
   (concat
    (reverse plugin-spells)
    spells5e/spells)))

;; ::spells5e/spells-map — spell_subs.cljs:1203-1211
(defn- spells-map [spells]
  (reduce
   (fn [m {:keys [name key level] :as spell}]
     (assoc m (or key (common/name-to-kw name)) spell))
   {}
   spells))

;; ::spells5e/plugin-spell-lists — spell_subs.cljs:1219-1233
(defn- plugin-spell-lists [plugin-spells]
  (reduce
   (fn [lists {:keys [key level spell-lists]}]
     (reduce-kv
      (fn [l k v]
        (if v
          (update-in l [k level] conj key)
          l))
      lists
      spell-lists))
   {}
   plugin-spells))

;; ::spells5e/spell-lists — spell_subs.cljs:1235-1242
(defn- spell-lists [plugin-spell-lists]
  (merge-with
   merge-spell-lists
   sl5e/spell-lists
   plugin-spell-lists))

;;; ---------------------------------------------------------------------------
;;; equipment_subs.cljs
;;; ---------------------------------------------------------------------------

;; ::mi5e/expanded-custom-items — equipment_subs.cljs:53-57
(defn- expanded-custom-items [custom-items]
  (mi5e/expand-magic-items custom-items))

;; ::char5e/sorted-items — equipment_subs.cljs:71-77. Named sorted-items-sub
;; because the body refers to the `sorted-items` delay copied above.
(defn- sorted-items-sub [custom-items]
  (concat
   custom-items
   @sorted-items))

;; ::mi5e/custom-weapons — equipment_subs.cljs:79-85
(defn- custom-weapons [custom-items]
  (sequence
   mi5e/magic-weapon-xform
   custom-items))

;; ::mi5e/custom-and-standard-weapons — equipment_subs.cljs:87-95
(defn- custom-and-standard-weapons [custom-weapons]
  (concat
   (map
    (fn [{:keys [::mi5e/name] :as i}]
      (assoc i :name name))
    custom-weapons) weapon5e/weapons))

;; ::mi5e/custom-and-standard-weapons-map — equipment_subs.cljs:97-101
(defn- custom-and-standard-weapons-map [custom-and-standard-weapons]
  (common/map-by-key custom-and-standard-weapons))

;; ::mi5e/magic-weapons — equipment_subs.cljs:103-109
(defn- magic-weapons [sorted-items]
  (sequence
   mi5e/magic-weapon-xform
   sorted-items))

;; ::mi5e/magic-weapon-map — equipment_subs.cljs:135-139
(defn- magic-weapon-map [magic-weapons]
  (map-by-key-or-id magic-weapons))

;; ::mi5e/magic-weapon-options — equipment_subs.cljs:166-169
(defn- magic-weapon-options [magic-weapons]
  ((magic-item-options mod5e/deferred-magic-weapon "Magic Weapon") magic-weapons nil))

;; ::mi5e/magic-armor-options — equipment_subs.cljs:171-174
(defn- magic-armor-options [magic-armor]
  ((magic-item-options mod5e/deferred-magic-armor "Magic Armor") magic-armor nil))

;; ::mi5e/other-magic-item-options — equipment_subs.cljs:176-179
(defn- other-magic-item-options [other-magic-items]
  ((magic-item-options mod5e/deferred-magic-item "Magic Item") other-magic-items nil))

;; ::mi5e/magic-armor — equipment_subs.cljs:181-187
(defn- magic-armor [sorted-items]
  (sequence
   mi5e/magic-armor-xform
   sorted-items))

;; ::mi5e/magic-armor-map — equipment_subs.cljs:189-193
(defn- magic-armor-map [magic-armor]
  (map-by-key-or-id magic-armor))

;; ::mi5e/other-magic-items — equipment_subs.cljs:195-201
(defn- other-magic-items [sorted-items]
  (sequence
   mi5e/other-magic-items-xform
   sorted-items))

;; ::mi5e/all-armor-map — equipment_subs.cljs:203-209
(defn- all-armor-map [magic-armor-map]
  (merge
   magic-armor-map
   armor5e/armor-map))

;; ::mi5e/all-weapons-map — equipment_subs.cljs:227-233
(defn- all-weapons-map [magic-weapons-map]
  (merge
   magic-weapons-map
   weapon5e/weapons-map))

;; ::char5e/template-selections — equipment_subs.cljs:289-326
(defn- template-selections [magic-weapon-options
                            magic-armor-options
                            other-magic-item-options
                            weapons-map
                            custom-and-standard-weapons
                            spell-lists
                            spells-map
                            backgrounds
                            races
                            classes
                            feats
                            language-map]
  (t5e/template-selections magic-weapon-options
                           magic-armor-options
                           other-magic-item-options
                           weapons-map
                           custom-and-standard-weapons
                           spell-lists
                           spells-map
                           backgrounds
                           races
                           classes
                           feats
                           language-map))

;; ::char5e/template — equipment_subs.cljs:328-332
(defn- template [template-selections]
  (t5e/template template-selections))

;;; ---------------------------------------------------------------------------
;;; Public
;;; ---------------------------------------------------------------------------

(defn build
  "The old app's template for `plugins`, the value it keeps in app-db under
  :plugins ({source-name plugin-map}; {} means SRD only), with no user
  logged in.

  Returns
    {:template        what @(subscribe [::char5e/template]) yields
     :all-weapons-map what @(subscribe [::mi5e/all-weapons-map]) yields
     :all-armor-map   what @(subscribe [::mi5e/all-armor-map]) yields}"
  [plugins]
  (let [;; roots
        plugins (plugins-sub {:plugins plugins})
        custom-items (custom-items)

        ;; plugin content
        plugin-vals (plugin-vals plugins)
        plugins-with-sources (plugins-with-sources plugins)

        ;; spells
        plugin-spells (plugin-spells plugin-vals)
        spell-lists (spell-lists (plugin-spell-lists plugin-spells))
        spells-map (spells-map (spells plugin-spells))

        ;; languages, backgrounds, feats, selections
        language-map (language-map (languages-sub (plugin-languages plugin-vals)))
        backgrounds (backgrounds (plugin-backgrounds plugin-vals))
        feats (feats (plugin-feats plugin-vals))
        selection-map (selection-map (plugin-selections plugin-vals))

        ;; magic items and weapons
        expanded-custom-items (expanded-custom-items custom-items)
        sorted-items (sorted-items-sub expanded-custom-items)
        magic-weapons (magic-weapons sorted-items)
        magic-armor (magic-armor sorted-items)
        custom-and-standard-weapons (custom-and-standard-weapons
                                     (custom-weapons expanded-custom-items))
        all-weapons-map (all-weapons-map (magic-weapon-map magic-weapons))
        all-armor-map (all-armor-map (magic-armor-map magic-armor))

        ;; races
        races (races (plugin-races plugin-vals)
                     (plugin-subraces-map (plugin-subraces plugin-vals))
                     spell-lists
                     spells-map
                     language-map)

        ;; classes
        classes (classes spell-lists
                         spells-map
                         (plugin-subclasses-map
                          (plugin-subclasses plugins-with-sources spell-lists spells-map selection-map))
                         language-map
                         (plugin-classes plugins-with-sources spell-lists spells-map selection-map)
                         (invocations (plugin-invocations plugin-vals))
                         (boons (plugin-boons plugin-vals))
                         (custom-and-standard-weapons-map custom-and-standard-weapons))]
    {:template (template
                (template-selections (magic-weapon-options magic-weapons)
                                     (magic-armor-options magic-armor)
                                     (other-magic-item-options (other-magic-items sorted-items))
                                     all-weapons-map
                                     custom-and-standard-weapons
                                     spell-lists
                                     spells-map
                                     backgrounds
                                     races
                                     classes
                                     feats
                                     language-map))
     :all-weapons-map all-weapons-map
     :all-armor-map all-armor-map}))
