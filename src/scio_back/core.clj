(ns scio-back.core
  (:require
    [scio-back.doc :as doc]
    [scio-back.cli :as cli]
    [clojure-ini.core :as ini]
    [clojure.tools.logging :as log])
  (:gen-class))

(defn read-config
  "Reads the configuration for this application."
  [file-name]
  (ini/read-ini file-name :keywordize? true))

(defn get-handler-from-opts-or-cfg
  "Loads a document handler name from ini config, cli option, or if both are present it chooses the ini."
  [options cfg]
  (let [opts-handler (:handler options)
        cfg-handler  (get-in cfg [:general :handler])]
    (cond
      (and (some? opts-handler) (nil? cfg-handler)) opts-handler
      (and (some? cfg-handler) (nil? opts-handler)) cfg-handler
      (every? some? [opts-handler cfg-handler]) (do
                                                  (log/info "handler found in cli and ini, choosing ini.")
                                                  opts-handler)
      :else (log/error "handler must be defined by cli or in ini config"))))

(defn main-loop
  "Launching the document handler."
  [options]
  (let [cfg (read-config (:config-file options))
        handler-name (get-handler-from-opts-or-cfg options cfg)]
    (doc/start-handler handler-name cfg)))

(defn -main
  "The scio-back application."
  [& args]
  (let [{:keys [options exit-message ok?]} (cli/parse-args args)]
    (if exit-message
      (cli/exit (if ok? 0 1) exit-message)
      (main-loop options))))

