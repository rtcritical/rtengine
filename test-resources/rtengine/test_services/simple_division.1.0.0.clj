;;; simple test service. extra comments up here to ensure file parses
;;; correctly
(ns rtengine.test-services.simple-division
  {:dependencies '[[org.clojure/clojure "1.9.0"] ; clojure is a required dep
                   [medley "1.0.0"] ;; not needed, but testing parsing
                   ;; correctly with comments
                   ]})

(defn simple-division
  "Service divides two numbers"
  [{:keys [dividend divisor]
    :or {dividend 1
         divisor 1}}]
  {:result (/ dividend divisor)})
