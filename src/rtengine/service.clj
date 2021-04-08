(ns rtengine.service
  (:require [classlojure.core :as classlojure]
            [mount.core :refer [defstate]]
            [nrepl.core :as nrepl]
            [rtengine.classloader :refer [make-service-classloader]]
            [rtengine.service-connection.core :refer [execute]]
            [rtengine.utils :as rtu]))

;; returned data must map with EDN, or the result is returned as a string
;; instead of a map
(defn- service-meta [cl service-qualified-sym]
  (classlojure/eval-in
   cl
   `(-> ~service-qualified-sym
        var
        meta
        (dissoc :inline :inline-arities :ns))))

(defstate ^{:on-reload :noop} service-ports
  :start (atom 10010)
  :stop nil)

(defn next-service-port! []
  (swap! service-ports inc))

(defn start-service-server!
  "Starts a service server within the service namespace"
  [classloader fqsn service-qualified-sym port]
  (classlojure/eval-in
   classloader
   `(do
      (require 'rtengine.service-connection.core)
      (def clojure.core/service-server
        (rtengine.service-connection.core/service-server
         ~service-qualified-sym ~fqsn ~port)))))

(defn- stop-service-server! [service]
  (classlojure/eval-in
     (:classloader service)
     '(.close clojure.core/service-server)))

(defprotocol IService
  (start [this])
  (stop [this]))

(defrecord Service [fqsn service-qualified-sym version classloader meta
                    service-port]
  IService
  (start [{:keys [classloader fqsn service-qualified-sym service-port]}]
    (start-service-server! classloader fqsn service-qualified-sym service-port))
  (stop [this]
    (stop-service-server! this)))

;; (defn make-service-repl
;;   "Starts a cider-nrepl server inside the classloader. Returns the port."
;;   [classloader]
;;   (let [port (next-service-port)]
;;     (classlojure/eval-in
;;      classloader
;;      `(do
;;         ;; (require 'cider.nrepl)
;;         ;; (def clojure.core/nrepl-server
;;         ;;   (nrepl.server/start-server
;;         ;;    :port ~port
;;         ;;    :handler cider.nrepl/cider-nrepl-handler))
;;     port))

;; (defn stop-service-repl [service]
;;   (classlojure/eval-in
;;    (:classloader service)
;;    '(.close clojure.core/nrepl-server)))

(defn make-service [service-file]
  (let [dependencies (rtu/extract-service-fn-dependencies service-file)
        updated-deps (concat '[[rtcritical/rtengine-service-connection "2.0.0"]]
                             dependencies)
        classloader (make-service-classloader service-file updated-deps)
        fqsn (rtu/extract-service-ns service-file)
        service-name (rtu/service-file->service-name service-file)
        service-qualified-sym (symbol fqsn service-name)
        service-port (next-service-port!)
        service-meta (service-meta classloader service-qualified-sym)
        version (rtu/service-file->service-version service-file)]
    (Service. fqsn service-qualified-sym version classloader service-meta
              service-port)))

(defn run-service [service inputs-map]
  ;; simple version does not perform well - eval creates a new class
  ;; each run, and 10ms latency - memory and latency no good
  ;; most ideal may be to figure out how to execute directly on method.
  ;; tried many different ways and failed, also wouldn't allow remote
  ;; execution for a multi-node setup
  ;; (classlojure/with-classloader (:classloader service)
  ;;   (.invoke (:service-qualified-sym service) (:service-method service) inputs-map))
  (let [{:keys [additional-inputs classloader fqsn
                service-qualified-sym service-port]} service]
    (execute service-port inputs-map)))
