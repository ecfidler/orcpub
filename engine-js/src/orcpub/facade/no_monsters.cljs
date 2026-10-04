(ns orcpub.facade.no-monsters
  "Stands in for orcpub.dnd.e5.monsters in the pubdoor build (ORC-42).

  The monster list is about 500 KB of source, and the character build never
  uses it. orcpub.dnd.e5.spell-subs requires orcpub.dnd.e5.monsters only for
  the old app's monster subscriptions, which the facade never subscribes
  to. shadow-cljs.edn aliases orcpub.dnd.e5.monsters to this namespace in
  the pubdoor build (:build-options :ns-aliases), so the list stays out of
  dist/pubdoor.js. The content build writes it to
  dist/content/monsters.json instead (orcpub.facade.content).

  It defines only the vars that spell-subs reads.")

(def monsters
  "The old app's monster list. Empty in the engine."
  ())

(def challenge-ratings
  "XP by challenge rating. Empty in the engine."
  {})
