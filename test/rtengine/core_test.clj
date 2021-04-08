(ns rtengine.core-test
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.test :refer :all]
            [mount.core :as mount]
            [rtengine.core :refer :all])
  (:import [java.util.concurrent Executors]))

(defn rtengine-fixture [f]
  (try
    (mount/start)
    (f)
    (finally
      (Thread/sleep 1000)
      (mount/stop))))

(use-fixtures :each rtengine-fixture)

(defn test-service-file []
  (io/file "test-resources/rtengine/test_services/simple_division.1.0.0.clj"))

(def test-service-fqsn "rtengine.test-services.simple-division")
(def test-service-version "1.0.0")
(def test-service-tag (service-tag test-service-fqsn test-service-version))

(deftest add-service-exceptions
  (testing "add service with non-existent file throws"
    (is (thrown? Exception (add-service (io/file "non-existent-file")))))
  (testing "add service with non-matching-service-ns-fn"
    (is (thrown? Exception (add-service (io/file "test-resources/rtengine/test_services/service_ns_fn_non_matching.clj"))))))

(deftest add-service-success-test
  (testing "add service is successful"
    (is (= test-service-tag (add-service (test-service-file))))
    (is (= {:result 5}  (run-service test-service-fqsn test-service-version
                                     {:dividend 10 :divisor 2})))))

(deftest add-service-twice-non-override-throws-test
  (testing "adding service twice throws"
    (is (= test-service-tag (add-service (test-service-file))))
    (is (thrown? Exception (add-service (test-service-file))))))


(defn service-file-new-version [service-file destination-dir new-version]
  (io/file destination-dir (-> service-file
                               .getName
                               (str/split #"\.")
                               first
                               (str "." new-version ".clj"))))

(defmacro with-tmp-test-service-file [[tmp-file-binding tmp-version] & body]
  `(let [ tf# (test-service-file)
         ~tmp-file-binding (service-file-new-version tf# "/tmp" ~tmp-version)]
     (try
       (io/copy tf# ~tmp-file-binding)
       ~@body
       (finally
         (.delete ~tmp-file-binding)))))

(defn replace-token [file match replacement]
  (spit file (str/replace (slurp file) match replacement)))

(deftest add-service-twice-override-test
  (testing "adding service twice with :override true is successful"
    (is (= test-service-tag (add-service (test-service-file))))
    (is (= {:result 5}  (run-service test-service-fqsn test-service-version
                                     {:dividend 10 :divisor 2})))
    (with-tmp-test-service-file [tmp-service-file test-service-version]
      (replace-token tmp-service-file ":result" ":results")
      (is (thrown? Exception (add-service tmp-service-file)))
      (is (= test-service-tag (add-service tmp-service-file :override true)))
      (is (= {:results 2} (run-service test-service-fqsn test-service-version
                                       {:dividend 10 :divisor 5}))))))

(deftest add-run-same-service-different-version
  (testing "adding and running service twice with different version is successful"
    (is (= test-service-tag (add-service (test-service-file))))
    (let [new-version "1.0.1"
          tag (service-tag test-service-fqsn new-version)]
      (with-tmp-test-service-file [tmp-service-file new-version]
        (replace-token tmp-service-file ":result" ":results")
        (is (= tag (add-service tmp-service-file)))
        (is (= {:result 5}  (run-service test-service-fqsn test-service-version
                                         {:dividend 10 :divisor 2})))
        (is (= {:results 2} (run-service test-service-fqsn new-version
                                         {:dividend 10 :divisor 5})))))))

(deftest add-run-service-with-version
  (testing "adding service and running is successful"
    (is (= test-service-tag (add-service (test-service-file))))
    (is (= {:result 3} (run-service test-service-fqsn test-service-version
                                    {:dividend 9
                                     :divisor 3})))))

(deftest get-service []
  (testing "add and get service versions"
    (is (= #{} (get-service-versions test-service-fqsn)))
    (is (= test-service-tag (add-service (test-service-file))))
    (is (= #{test-service-version} (get-service-versions test-service-fqsn)))
    (let [new-version "1.0.1"
          tag (service-tag test-service-fqsn new-version)]
      (with-tmp-test-service-file [tmp-service-file new-version]
        (is (= tag (add-service tmp-service-file)))
        (is (= #{test-service-version new-version}
               (get-service-versions test-service-fqsn)))))))

(deftest remove-services-multiple-versions []
  (testing "running and removing multiple versions of service"
    (is (= test-service-tag (add-service (test-service-file))))
    (let [new-version "1.0.1"
          tag (service-tag test-service-fqsn new-version)]
      (with-tmp-test-service-file [tmp-service-file new-version]
        (replace-token tmp-service-file ":result" ":results")
        (is (= tag (add-service tmp-service-file)))
        (is (= {:result 5}  (run-service test-service-fqsn test-service-version
                                         {:dividend 10 :divisor 2})))
        (is (= {:results 2} (run-service test-service-fqsn new-version
                                         {:dividend 10 :divisor 5})))
        
        (remove-service test-service-fqsn test-service-version)
        (is (thrown? Exception (run-service test-service-fqsn
                                            test-service-version
                                            {:dividend 10 :divisor 2})))
        (is (= {:results 5}  (run-service test-service-fqsn new-version
                                          {:dividend 10 :divisor 2})))

        (remove-service test-service-fqsn new-version)
        (is (thrown? Exception (run-service test-service-fqsn
                                            test-service-version
                                            {:dividend 10 :divisor 2})))
        (is (thrown? Exception (run-service test-service-fqsn new-version
                                            {:dividend 10 :divisor 2})))))))

(deftest ^:performance perf-test-parallel
  (testing "running service many times in parallel, asserting < 2 ms per run"
    (is (= test-service-tag (add-service (test-service-file))))
    (let [pool-size 4
          tp (Executors/newFixedThreadPool pool-size)
          run-count 10000
          tasks (for [n (range run-count)]
                  #(run-service test-service-fqsn
                                test-service-version
                                {:dividend 10 :divisor 2}))
          start (System/currentTimeMillis)
          futures (map deref (.invokeAll tp tasks))
          duration (- (System/currentTimeMillis) start)
          ms-per-run (double (* pool-size (/ duration run-count)))]
      (println "perf-test ms/run: " ms-per-run " ms")
      (is (< ms-per-run 2)
          "less than 2 milliseconds per service run in parallel"))))
