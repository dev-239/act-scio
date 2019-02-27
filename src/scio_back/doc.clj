(ns scio-back.doc
  (:require
   [scio-back.tag :as tag]
   [scio-back.storage :as storage]
   [pantomime.extract :as extract]
   [scio-back.nlp :as nlp]
   [scio-back.scraper :as scraper]
   [clojure.set :refer [difference]]
   [clojure.core.async :refer  [>!! <!! chan thread]]
   [clojure.data.json :as json]
   [clojure.java.io :as io]
   [clojure.stacktrace :as stacktrace]
   [clojure.tools.logging :as log]
   [digest]
   [beanstalk-clj.core :refer [with-beanstalkd beanstalkd-factory
                               put delete reserve
                               watch-tube use-tube]])
  (:import [info.debatty.java.spamsum SpamSum]
           (java.util.concurrent TimeUnit TimeoutException)
           (java.util TimeZone Date)
           (java.text SimpleDateFormat)
           (java.io ByteArrayOutputStream FileNotFoundException)))


(defmacro with-timeout [millis name & body]
  `(let [future# (future ~@body)]
     (try
       (.get future# ~millis TimeUnit/MILLISECONDS)
       (catch TimeoutException x#
         (do
           (future-cancel future#)
           (log/error ~name "Timed out")
           nil)))))

(def NA '("NA"))

(defn iso-date-string
  []
  (let [tz (TimeZone/getTimeZone "UTC")
        df (SimpleDateFormat. "yyyy-MM-dd'T'HH:mm'Z'")
        now (Date.)]
    (.format (doto df
               (.setTimeZone tz))
             now)))

(defn build-data-map
  "Extract specific fields from the document. If the document does not contain
  a 'creation-date' field, the current time is used."
  [doc file-name]
  (-> {}
      (into {:creation-date (first (get doc :creation-date [(iso-date-string)]))})
      (into {:creator (first (get doc :dc/creator NA))})
      (into {:description (first (get doc :dc/description NA))})
      (into {:text (get doc :text NA)})
      (into {:format (first (get doc :dc/format NA))})
      (into {:title (first (get doc :dc/title [(.getName (io/file file-name))]))})
      (into {:author (first (get doc :author NA))})
      (into {:creator-tool (first (get doc :creator-tool NA))})))

(defn analyse
  "Analyse file and store result to elasticsearch"
  [file-name cfg sha-256]
  (log/info "Starting to analyze" sha-256)
  (try
    (let [doc (extract/parse file-name)
          spamsum (SpamSum.)
          content (:text doc)
          indicators (scraper/raw-text->indicators cfg content)
          nlp (nlp/raw-text->interpretation cfg :en content)
          tag-list (concat
                    (get nlp :threatactors)
                    (get nlp :organizations)
                    (get nlp :locations)
                    (get nlp :persons))
          cfg-cities (get-in cfg [:geonames :cities])
          cfg-countries (get-in cfg [:geonames :country-info])
          cfg-regions (get-in cfg [:geonames :regions])
          cfg-tools (get-in cfg [:tools :tools-config])
          ;; NLP locations can contain words such as Yes and No.. filter out
          ;; shorter than three word locations as these can be mistaken for
          ;; ISO Short Country Codes.
          nlp-locations (filter #(< 2 (count %)) (get nlp :locations))
          geonames-cities (tag/locations cfg-cities nlp-locations)
          geonames-tags (tag/location-aliases cfg-cities nlp-locations)
          geonames-countries (tag/country-info cfg-countries nlp-locations)
          geonames-cc-derived (tag/locations->country-codes cfg-cities
                                                            geonames-cities)
          geonames-countries-derived (tag/country-info cfg-countries
                                                       geonames-cc-derived)
          geonames-regions (tag/region-info cfg-regions nlp-locations :region)
          geonames-sub-regions (tag/region-info cfg-regions nlp-locations :sub-region)
          geonames-countries-regions-derived (tag/country->region
                                              cfg-regions
                                              geonames-countries
                                              :region)
          geonames-countries-sub-regions-derived (tag/country->region cfg-regions
                                                                      geonames-countries
                                                                      :sub-region)
          geonames-countries-derived-regions-derived (tag/country->region cfg-regions
                                                                          geonames-countries-derived
                                                                          :region)
          geonames-countries-derived-sub-regions-derived (tag/country->region cfg-regions
                                                                              geonames-countries-derived
                                                                              :sub-region)
          tools (set (tag/tools cfg-tools content))]
      (-> doc
          (build-data-map file-name)
          (into {:indicators indicators
                 :nlp nlp
                 :hexdigest sha-256
                 :ssdeep (.HashString spamsum content)
                 :sectors (tag/find-sectors (:sectors cfg) content)
                 :threat-actor {:names (tag/threat-actors
                                        (get-in cfg [:threatactors :ta-config])
                                        tag-list)
                                :aliases (tag/threat-actor-aliases
                                          (get-in cfg [:threatactors :ta-config])
                                          tag-list)}
                 :geonames {:cities geonames-cities
                            :tags geonames-tags
                            :countries geonames-countries
                            :regions geonames-regions
                            :sub-regions geonames-sub-regions
                            :regions-derived geonames-countries-regions-derived
                            :sub-regions-derived geonames-countries-sub-regions-derived
                            :countries-derived (difference geonames-countries-derived
                                                           geonames-countries)
                            :countries-derived-regions-derived (difference geonames-countries-derived-regions-derived
                                                                               geonames-countries-regions-derived)
                            :countries-derived-sub-regions-derived (difference geonames-countries-derived-sub-regions-derived
                                                                           geonames-countries-sub-regions-derived)}
                 :tools {:names (map :name tools)
                         :aliases (flatten (map :aliases tools))}})))
    (catch Exception e
      (log/error (with-out-str (stacktrace/print-stack-trace e)))
      nil)))

(defn start-document-worker
  "Start a new document worker, listening for jobs on a channel"
  [job-channel cfg id]
  (thread
    (while true
      (let [[file-name sha256 record] (<!! job-channel)
            storage-cfg (:storage cfg)]
        (log/info "Worker" id "got job" sha256)
        (let [ms-running-time (* 1000 60 5)] ;; 5 min max running time
          (if-let [a (with-timeout ms-running-time sha256
                       (analyse file-name cfg sha256))]
            (do
              (storage/send-to-nifi (into a record) storage-cfg sha256)
              (log/info "Worker" id "finished job" sha256))
            (log/error "Worker" id "FAILED to complete job" sha256)))))))

(defn start-worker-pool
  "Spin up a pool of n workers listening to a jobs channel"
  [cfg num-workers]
  (let [job-channel (chan)]
    (log/info "Starting a worker pool of" num-workers "workers")
    (doseq [id (range num-workers)]
      (log/info "Starting worker" id)
      (start-document-worker job-channel cfg id))
    (log/info "Worker pool started")
    job-channel))

(defn store!
  "Store a file to disk. If it already exists; do nothing"
  [content output-name]
  (when-not (.exists (io/file output-name))
    (with-open [w (io/output-stream output-name)]
      (.write w content))))

(defn slurp-bytes
  "Slurp the bytes from a slurpable thing"
  [x]
  (try
    (with-open [out (ByteArrayOutputStream.)]
      (io/copy (io/input-stream x) out)
      (.toByteArray out))
    (catch FileNotFoundException e
      (log/warn "Could not read:" x)
      nil)))

(defn handle-from-beanstalk
  "Handles an incoming message on a tube in beanstalk, stores the file content of
  the message to disk (as per .ini file config). Then sends file to analysis"
  [job-channel cfg]
  (let [bs-cfg (get cfg :beanstalk {:host "localhost" :port 11300 :queue "doc"})
        host (:host bs-cfg)
        port (Integer. (:port bs-cfg))
        queue (get bs-cfg :queue bs-cfg)]
    (while true
      (with-beanstalkd (beanstalkd-factory host port)
        (watch-tube queue)
        (let [job (reserve)
              record (json/read-str (.body job) :key-fn keyword)
              content (slurp-bytes (:filename record))
              sha-256 (digest/sha-256 content)
              output-name (str (get-in cfg [:storage :storagedir]) "/" (.getName (io/file (:filename record))))]
          (when content
            (store! content output-name)
            (>!! job-channel [output-name sha-256 record]))
          (delete job))))))

(def handlers
  "Document handlers available for the user to run the system with"
  {:beanstalk handle-from-beanstalk})

(defn start-handler
  "Starts a worker pool, then starts the wanted document handler to ingest docs into the worker pool"
  [handler-name cfg]
  (let [worker-count (Integer. (get-in cfg [:general :worker-count] 1))
        job-channel (start-worker-pool cfg worker-count)
        handler-fn (get handlers (keyword handler-name))]
    (if (some? handler-fn)
      (handler-fn job-channel cfg)
      (log/error "document handler" handler-name "is not registered in the system"))))
