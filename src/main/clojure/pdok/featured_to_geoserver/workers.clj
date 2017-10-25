(ns pdok.featured-to-geoserver.workers
  (:require [clojure.tools.logging :as log]
            [clojure.java.io :as io]
            [pdok.featured-to-geoserver
             [core :as core]
             [config :as config]
             [database :as database]]
            [clojure.core.async :as async])
  (:import (com.netflix.conductor.client.worker Worker)
           (com.netflix.conductor.common.metadata.tasks TaskResult TaskResult$Status Task)
           (java.io File)
           (java.util.zip ZipFile ZipEntry)
           (java.sql Connection)))

(defn download-uri [^String uri]
  "Copy to tmp file, return handle"
  (let [file (File/createTempFile "tmpfile" nil)]
    (with-open [in (io/input-stream uri)
                out (io/output-stream file)]
      (io/copy in out))
    file))

(defn unzip [^File file]
  (let [zipfile (ZipFile. file)
        ^ZipEntry entry (first (enumeration-seq (.entries zipfile)))
        name (.getName entry)
        ^File tmp-dir (File/createTempFile "unzip" nil)
        _ (do (.delete tmp-dir) (.mkdirs tmp-dir))
        target (File. tmp-dir name)]
    (with-open [in (.getInputStream zipfile entry)
                out (io/output-stream target)]
      (io/copy in out))
    target))

(deftype Loader [^String name]
  Worker
  (getTaskDefName [this] name)
  (execute [this task]
    (let [^TaskResult result (doto (TaskResult. task) (.setStatus TaskResult$Status/FAILED))
          output (.getOutputData result)]
      (try
        (log/info "Working ...")
        (let [input (.getInputData task)
              dataset (.get input "dataset")
              file-uri (.get input "uri")
              local-zip (download-uri file-uri)
              unzipped (unzip local-zip)
              db (config/db)
              process-channel (async/chan 1)
              terminate-channel (async/chan 1)]
          (core/create-workers 1 db process-channel terminate-channel)
          (async/>!! process-channel {:file           unzipped
                                      :dataset        dataset
                                      :format         "plain"
                                      :mapping        {}
                                      :exclude-filter {}})
          (async/close! process-channel)
          (async/<!! terminate-channel)
          (do (.delete local-zip)))
        (log/info "... Finished")
        (doto result (.setStatus TaskResult$Status/COMPLETED))
        (catch Exception e (log/error e) (log/info "... Finished") result)))))
