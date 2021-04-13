(defproject rtcritical/rtengine "1.0.1"
  :description "Minimal clojure service engine that allows deploying single file services at runtime with their own isolated dependencies. Dependencies are downloaded as necessary. Multiple versions of a service can coexist in the system and be individually ran."
  :url "https://github.com/RTCritical/RTEngine"
  :license {:name "MIT"
            :url  "http://www.opensource.org/licenses/mit-license.php"}
  :dependencies [[clj-commons/pomegranate "1.2.0"]
                 [mount "0.1.16"]
                 [org.clojure/clojure "1.10.0"]
                 [org.clojure/tools.logging "1.1.0"]
                 [org.flatland/classlojure "0.7.1"]
                 [rtcritical/rtengine-service-connection "2.0.0"]]
  :plugins []
  :profiles {:dev {:dependencies []}})
