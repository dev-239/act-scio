(ns scio-back.storage
    (:require
     [clj-http.client :as client]
     [clojure.data.json :as json]))

(defn send-to-elasticsearch
  "Send a data structure to Elasticsearch"
  [data cfg sha-256]
  (let [document (json/write-str data)
        schema (:schema cfg)
        host (:host cfg)
        port (Integer/parseInt (:port cfg))
        index (:index cfg)
        doc-type (:doc-type cfg)
        url (str schema "://" host ":" port "/" index "/" doc-type "/" sha-256)]
    (client/post url
      {:body document
       :content-type :json
       :accept :json})))

(defn send-to-nifi
  "Send a data structure to NiFi"
  [data cfg _]
  (let [document (json/write-str data)
        url (:nifi-url cfg)]
    (client/post url
      {:body document
       :content-type :json
       :accept :json})))

(def stores
  "A map of available stores the user can send analysis results to."
  {:elasticsearch send-to-elasticsearch
   :nifi send-to-nifi})