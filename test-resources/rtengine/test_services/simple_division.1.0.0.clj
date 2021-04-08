(ns rtengine.test-services.simple-division)

(defn simple-division
  "Service divides two numbers"
  {:dependencies '[[org.clojure/clojure "1.9.0"]]}
  [{:keys [dividend divisor]
    :or {dividend 1
         divisor 1}}]
  {:result (/ dividend divisor)})
