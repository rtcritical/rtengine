(ns rtengine.core
  (:require [clojure.tools.logging :as log]
            [mount.core :refer [defstate]]
            [rtengine.service :as service]))

(defn- extract-service-objs [services]
  (reduce (fn [accum service-version-map]
            (concat accum (vals service-version-map)))
          []
          (vals services)))

(defn- stop-services [services]
  (doseq [service (extract-service-objs services)]
    (.stop service)))

;; holds map {"some.test.service-name" {"0.1.0" service-obj}}
(defstate ^{:on-reload :noop} services
  :start (atom {})
  :stop (swap! services stop-services))

(defn service-tag [fqsn version]
  (format "%s:%s" fqsn version))

(defn run-service
  ([fqsn inputs-map]
   (run-service fqsn nil inputs-map))
  ([fqsn version inputs-map]
   (let [tag (service-tag fqsn version)]
     (log/tracef "Running service %s with inputs: " tag inputs-map)
     (let [service (get-in @services [fqsn version])]
       (if-not service
         (throw (Exception. (format "Service %s is not in the system" tag)))
         (service/run-service service inputs-map))))))

;; override optional arg - useful during development
(defn add-service [service-file & {:keys [override]}]
  (let [new-service (service/make-service service-file)
        version (:version new-service)
        fqsn (:fqsn new-service)
        tag (service-tag fqsn version)
        deployed-versions (get @services fqsn)
        deployed-version (get deployed-versions version)]
    (log/infof "Adding service %s" tag)
    (when (and (not override) deployed-version)
      (throw (Exception. (format "%s already deployed" tag))))
    (when (and override deployed-version)
      (log/infof "Overriding service %s" tag))
    (.start new-service)
    (swap! services assoc-in [fqsn version] new-service)
    (if deployed-version
      (.stop deployed-version))
    tag))

(defn get-service-versions [fqsn]
  (log/infof "Returning service versions for %s" fqsn)
  (set (keys (get @services fqsn))))

(defn remove-service [fqsn version]
  (let [tag (service-tag fqsn version)]
    (log/infof "Removing service %s" tag)
    (let [versions (get @services fqsn)
          service (get versions version)]
      (if-not (contains? versions version)
        (log/warnf "Cannot remove %s. Not in the system." tag)
        (do
          (.stop service)
          (if (= 1 (count versions))
            (swap! services dissoc fqsn)
            (swap! services update-in [fqsn] dissoc version))))
      (log/infof "Successfully removed %s" tag)
      tag)))


