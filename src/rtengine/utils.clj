(ns rtengine.utils
  (:require [clojure.string :as str]))

(defn service-file->service-name [service-file]
  (-> service-file
      .getName
      (str/split #"\.")
      first
      (str/replace "_" "-")))

(defn service-file->service-version [service-file]
  (->> (-> service-file
        .getName
        (str/split #"\.")
        rest)
       (take 3)
       (str/join ".")))

(defn extract-service-ns [service-file]
  (let [service-name (service-file->service-name service-file)
        service-file-contents (slurp service-file)]
    (-> (str "\\(ns\\s+([^\\s|\\)]+)")
        re-pattern
        (re-find service-file-contents)
        second)))

(defn extract-service-fn-dependencies [service-file]
  (let [service-name (service-file->service-name service-file)
        service-file-contents (slurp service-file)
        pattern (re-pattern (str "(?s).*\\(ns\\s+[a-zA-Z0-9.-]*" service-name "\\s+(?:\"[^\"]*\")?\\s+\\{(?:.*\\s+|):dependencies\\s+'(.*].*])"))]
    (when-let [results (re-find pattern service-file-contents)] 
      (read-string (second results)))))
