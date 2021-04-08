(ns rtengine.classloader
  (:require [cemerick.pomegranate :as pomegranate]
            [cemerick.pomegranate.aether :as aether]
            [classlojure.core :as classlojure]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [rtengine.utils :as rtu])
  (:import [java.net URL URLClassLoader]
           [java.io StringReader]
           [clojure.lang DynamicClassLoader]))

;; TODO: Somehow re-use classes/dependencies between services that are
;;       common
;; - one possiblity...a classloader for each dependency, and then
;;   each service classloader has reference and delegates to the
;;   dependency classloader. This would isolate and re-use..
;; TODO: Make service classloader non-modifiable...goes against clojure!...
;;       could work when in production mode to help secure the code.
(defn- make-classloader
  "dependencies -> '[[dep1 \"0.1.1\"]]"
  [dependencies & {:keys [repositories]
                   :or {repositories (merge aether/maven-central
                                            {"clojars" "https://clojars.org/repo"})}}]
  (let [service-classloader (DynamicClassLoader. classlojure/ext-classloader)]
    (when-not (some #(= 'org.clojure/clojure (first %)) dependencies)
       (throw (Exception. "Missing a org.clojure/clojure dep")))
    (pomegranate/add-dependencies :classloader service-classloader
                                  :coordinates dependencies
                                  :repositories repositories)
    service-classloader))

;; TODO: Disallow downloading dependencies. Pomegranate/aether allows
;;       giving a local-repo that could be a lib/ directory, offline?,
;;       etc..
;; seems should have a 'deploy directory with deployed service files,
;; and use those here, so the compiler has a source file associated...
(defn make-service-classloader [service-file service-dependencies]
  (let [service-classloader (make-classloader service-dependencies)
        result (classlojure/eval-in service-classloader
                                    `(Compiler/loadFile ~(.getAbsolutePath service-file)))]
    service-classloader))
