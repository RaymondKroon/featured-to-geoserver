(ns pdok.featured-to-geoserver.cli
  (:require [clojure.core.async :as async]
            [clojure.string :as str]
            [clojure.tools.logging :as log]
            [clojure.tools.cli :as cli]
            [environ.core :refer [env]]
            [pdok.featured-to-geoserver
             [util :refer :all]
             [config :as config]
             [core :as core]
             [workers :as workers]])
  (:gen-class)
  (:import (com.netflix.conductor.client.http TaskClient)
           (com.netflix.conductor.client.task WorkflowTaskCoordinator$Builder)
           (pdok.featured_to_geoserver.workers Loader)))

(defn- parse-collection-field [x]
  (str/split x #"/"))

(def ^:private validate-collection-field
  [#(= 2 (count %)) "Must be COLLECTION/FIELD"])

(defn- assoc-mapping [type]
  (fn [m k [collection field]]
    (update-in
      m
      [k (keyword collection) type]
      #(conj (or % []) field))))

(def cli-options
  [[nil "--conductor-client"]
   ["-f" "--format  FORMAT" "File format (zip or plain)"
    :default "zip"
    :validate [#(some (partial = %) ["zip" "plain"]) "File format must be zip or plain"]]
   [nil "--db-host HOST" "Database host"
    :default "localhost"]
   [nil "--db-port PORT" "Database port"
    :default 5432
    :parse-fn #(Integer/parseInt %)
    :validate [#(> % 0) "Port number should be positive"]]
   [nil "--db-user USER" "Database user"
    :default "postgres"]
   [nil "--db-password PASSWORD" "Database password"
    :default "postgres"]
   [nil "--db-name DATABASE" "Database name"
    :default "pdok"]
   [nil "--array COLLECTION/FIELD" "Specify array mapping for field"
    :parse-fn parse-collection-field
    :validate validate-collection-field
    :assoc-fn (assoc-mapping :array)
    :default {}]
   [nil "--unnest COLLECTION/FIELD" "Specify unnesting on field"
    :parse-fn parse-collection-field
    :validate validate-collection-field
    :assoc-fn (assoc-mapping :unnest)
    :default {}]
   [nil "--exclude-filter FIELD=EXCLUDE-VALUE" "Exclude changelog entries"
    :parse-fn #(str/split % #"=")
    :validate [#(= 2 (count %)) "Must be FIELD=EXCLUDE-VALUE"]
    :assoc-fn (fn [m k [field exclude-value]] (update-in m [k (keyword field)] #(conj (or % []) exclude-value)))
    :default {}]])

(defn log-usage [summary]
  (log/info "")
  (log/info "Usage: lein run [options] dataset files")
  (log/info "")
  (log/info "Options")
  (doseq [option (str/split summary #"\n")]
    (log/info option))
  (log/info ""))

(defn- merge-mapping [& maps]
  (apply (partial merge-with merge) maps))

(defn run-client []
  (log/info "TEST")
  (let [client (doto (TaskClient.)
                 (.setRootURI (env :conductor-api-root)))
        coordinator (.build (doto (WorkflowTaskCoordinator$Builder.)
                              (.withWorkers [(Loader. "featured_to_geoserver")])
                              (.withThreadCount (env :thread-count 1))
                              (.withTaskClient client)))]
    (.init coordinator)))

(defn -main [& args]
  (log/info "This is the featured-to-geoserver CLI version" (implementation-version))
  (let [{[dataset & files] :arguments summary :summary options :options errors :errors} (cli/parse-opts args cli-options)
        {format :format
         host :db-host
         port :db-port
         user :db-user
         password :db-password
         database :db-name
         array :array
         unnest :unnest
         exclude-filter :exclude-filter} options]
    (cond
      (:conductor-client options) (run-client)
      errors (do (log-usage summary) (doseq [error errors] (log/error error)))
      (empty? files) (log-usage summary)
      :else (let [db {:url (str "//" host ":" port "/" database)
                      :user user
                      :password password}
                  process-channel (async/chan 1)
                  terminate-channel (async/chan 1)]
              (core/create-workers 1 db process-channel terminate-channel)
              (doseq [file files]
                (log/info "Processing file:" file)
                (async/>!! process-channel {:file file 
                                            :dataset dataset 
                                            :format format
                                            :mapping (merge-mapping array unnest)
                                            :exclude-filter exclude-filter}))
              (async/close! process-channel)
              (log/info (str "Worker " (async/<!! terminate-channel) " terminated"))
              (log/info "Application terminated")))))
