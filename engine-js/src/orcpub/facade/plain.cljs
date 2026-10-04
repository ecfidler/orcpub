(ns orcpub.facade.plain
  "Plain-data conversion: ClojureScript values to the JSON-shaped data the
  facade returns and the content build writes (orcpub.oracle/->plain in
  scripts/orcpub/oracle.clj; fixtures/README.md documents the rules for
  expected.json). The facade and orcpub.facade.content share it, so built
  values, template shapes, and the content lists encode keywords alike.")

(defn kw->str
  "A keyword as \"name\", or \"ns/name\" when it has a namespace."
  [k]
  (if (namespace k) (str (namespace k) "/" (name k)) (name k)))

(defn- simple-key->str [k]
  (cond (keyword? k) (kw->str k)
        (string? k) k
        (number? k) (str k)
        (boolean? k) (str k)
        (nil? k) "nil"
        :else ::composite))

(declare convert)

(defn- sort-plain [coll]
  (try (vec (sort coll))
       (catch :default _ (vec (sort-by pr-str coll)))))

(defn- kept-entries [{:keys [data? omit-keys]} m]
  (if data?
    (remove (fn [[k v]] (or (fn? v) (contains? omit-keys k))) m)
    m))

(defn- plain-map [opts m]
  (let [entries (kept-entries opts m)]
    (if (some #{::composite} (map (comp simple-key->str key) entries))
      ;; For example spells-known, keyed by [class-name spell-key].
      {"__entries" (->> entries
                        (map (fn [[k v]] [(convert opts k) (convert opts v)]))
                        (sort-by (comp pr-str first))
                        vec)}
      (into {} (map (fn [[k v]] [(simple-key->str k) (convert opts v)])) entries))))

(defn- convert [{:keys [data?] :as opts} x]
  (cond (nil? x) nil
        (string? x) x
        (boolean? x) x
        (keyword? x) (kw->str x)
        (symbol? x) (str x)
        (number? x) x
        (map? x) (plain-map opts x)
        (set? x) (sort-plain (map #(convert opts %) (cond->> x data? (remove fn?))))
        (sequential? x) (into [] (comp (if data? (remove fn?) identity)
                                       (map #(convert opts %)))
                              x)
        (fn? x) "#fn"
        data? (throw (ex-info (str "No plain-data form for " (pr-str x))
                              {:value x}))
        :else (pr-str x)))

(defn ->plain
  "keyword → \"ns/name\", set → sorted vector, map → string-keyed map
  (composite keys → {\"__entries\" [[k v] ...]}), fn → \"#fn\", anything
  else unknown → pr-str. ClojureScript has no ratios."
  [x]
  (convert nil x))

(defn ->data
  "->plain for data written to files: functions and the map entries whose
  key is in omit-keys are left out, and a value ->plain would pr-str
  throws, so nothing unexpected is written."
  [omit-keys x]
  (convert {:data? true :omit-keys omit-keys} x))
