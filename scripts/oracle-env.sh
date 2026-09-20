#!/usr/bin/env bash
# Build a JVM classpath for the Phase A oracle scripts WITHOUT Leiningen or
# Clojars, and run a Clojure entry point on it.
#
# The supported path is `lein run -m clojure.main scripts/<script>.clj ...`
# (see fixtures/README.md). This fallback exists for environments where
# Clojars is unreachable: everything the engine namespaces need on the JVM
# is either on Maven Central (downloaded here) or pure-Clojure source on
# GitHub (cloned here at a pinned ref). Nothing is installed globally; all
# artifacts land in scripts/.oracle-deps/ (git-ignored).
#
# Usage:
#   scripts/oracle-env.sh --cp                 # print the classpath
#   scripts/oracle-env.sh test                 # run the engine test namespaces
#   scripts/oracle-env.sh scripts/dump-built-character.clj <args...>
#   scripts/oracle-env.sh -e '(println :hi)'   # any clojure.main args
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DEPS="$ROOT/scripts/.oracle-deps"
JARS="$DEPS/jars"
SRC="$DEPS/src"
MAVEN="https://repo1.maven.org/maven2"

# Versions match project.clj (clojure, data.json, test.check, transit-*) or
# the library's own declared dependencies (spec.alpha, core.specs.alpha,
# tools.logging, macrovich, jackson, msgpack, jaxb-api).
MAVEN_ARTIFACTS=(
  org/clojure/clojure/1.12.4/clojure-1.12.4.jar
  org/clojure/spec.alpha/0.5.238/spec.alpha-0.5.238.jar
  org/clojure/core.specs.alpha/0.4.74/core.specs.alpha-0.4.74.jar
  org/clojure/data.json/2.5.0/data.json-2.5.0.jar
  org/clojure/tools.logging/1.3.0/tools.logging-1.3.0.jar
  org/clojure/tools.reader/1.5.2/tools.reader-1.5.2.jar
  org/clojure/test.check/1.1.1/test.check-1.1.1.jar
  com/cognitect/transit-clj/1.0.333/transit-clj-1.0.333.jar
  com/cognitect/transit-java/1.0.371/transit-java-1.0.371.jar
  com/fasterxml/jackson/core/jackson-core/2.14.2/jackson-core-2.14.2.jar
  org/msgpack/msgpack/0.6.12/msgpack-0.6.12.jar
  javax/xml/bind/jaxb-api/2.4.0-b180830.0359/jaxb-api-2.4.0-b180830.0359.jar
)

# name|git url|ref|source subdir   (pure-Clojure libraries hosted on Clojars)
GIT_SOURCES=(
  "re-frame|https://github.com/day8/re-frame.git|v1.4.4|src"
  "macrovich|https://github.com/cgrand/macrovich.git|95f0fa924ec774a9fa6845f474c2aba7322fea14|src"
  "bidi|https://github.com/juxt/bidi.git|2.1.6|src"
)

mkdir -p "$JARS" "$SRC"

for a in "${MAVEN_ARTIFACTS[@]}"; do
  f="$JARS/${a##*/}"
  if [ ! -f "$f" ]; then
    echo "fetching $a" >&2
    curl -sS --fail --location --max-time 300 -o "$f" "$MAVEN/$a"
  fi
done

for entry in "${GIT_SOURCES[@]}"; do
  IFS='|' read -r name url ref subdir <<<"$entry"
  d="$SRC/$name"
  if [ ! -d "$d/.git" ]; then
    echo "cloning $name@$ref" >&2
    if ! git clone -q --depth 1 --branch "$ref" "$url" "$d" 2>/dev/null; then
      # a bare commit sha cannot be used with --branch
      git clone -q "$url" "$d"
      git -C "$d" checkout -q "$ref"
    fi
  fi
done

CP="$ROOT/src/cljc:$ROOT/src/cljs:$ROOT/test/cljc:$ROOT/scripts"
for entry in "${GIT_SOURCES[@]}"; do
  IFS='|' read -r name url ref subdir <<<"$entry"
  CP="$CP:$SRC/$name/$subdir"
done
for j in "$JARS"/*.jar; do CP="$CP:$j"; done

case "${1:-}" in
  --cp) echo "$CP"; exit 0 ;;
  test)
    shift
    cd "$ROOT"
    exec java -cp "$CP" clojure.main -e "
      (require 'clojure.test)
      (def nss '[orcpub.dnd.e5.warlock-test orcpub.dnd.e5.character-test
                 orcpub.dnd.e5.event-handlers-test orcpub.entity-test orcpub.template-test])
      (doseq [n nss] (require n))
      (let [r (apply clojure.test/run-tests nss)]
        (System/exit (if (zero? (+ (:fail r) (:error r))) 0 1)))" "$@" ;;
  *)
    cd "$ROOT"
    exec java -cp "$CP" clojure.main "$@" ;;
esac
