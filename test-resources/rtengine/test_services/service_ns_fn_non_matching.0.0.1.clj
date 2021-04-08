(ns rtengine.test-services.service-ns-fn-non-matching)

(defn my-service-fn-does-not-match-ns
  ""
  {:dependencies '[[org.clojure/clojure "1.9.0"]]}
  []
  "result")
