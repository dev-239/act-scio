(ns scio-back.storage
    (:require
     [clj-http.client :as client]
     [clojure.data.json :as json]))

(defn send-to-elasticsearch
  "Send a data structure to Elasticsearch"
  [data storage-cfg sha-256]
  (let [document (json/write-str data)
        host (:elasticsearch-url storage-cfg)
        index (:elasticsearch-index storage-cfg)
        doc-type (:elasticsearch-doc-type storage-cfg)
        url (str host "/" index "/" doc-type "/" sha-256)]
    (client/post url
      {:body document
       :content-type :json
       :accept :json})))

(defn send-to-nifi
  "Send a data structure to NiFi"
  [data storage-cfg _]
  (let [document (json/write-str data)
        url (:nifi-url storage-cfg)]
    (client/post url
      {:body document
       :content-type :json
       :accept :json})))

(def stores
  "A map of available stores the user can send analysis results to."
  {:elasticsearch send-to-elasticsearch
   :nifi send-to-nifi})