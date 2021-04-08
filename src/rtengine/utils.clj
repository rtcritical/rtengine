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
        service-file-contents (slurp service-file)]
    (-> (str "\\(defn\\s+" service-name "\\s+(?:\"[^\"]*\")?\\s+\\{(?:.*\\s+|):dependencies\\s+'(.*]])")
      re-pattern
      (re-find service-file-contents)
      second
      read-string)))
