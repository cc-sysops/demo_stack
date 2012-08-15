(ns demo_stack.middleware
  (:use demo_stack.logger
        demo_stack.graphite
        cheshire.core)
  (:require [clj-stacktrace.repl :as stacktrace]
            [clojure.string :as string]))

(defn wrap-exception-logging [handler]
  (fn [request]
    (try
      (handler request)
      (catch Exception error
        (log "exception:\n%s" (stacktrace/pst-str error))
        (throw error)))))

(defn wrap-failsafe [handler]
  (fn [request]
    (try
      (handler request)
      (catch Exception error
        {:status 500
         :body (generate-string {:error (str error)})}))))

(defn wrap-content-type [handler]
  (fn [request]
    (let [response (handler request)]
      (assoc-in response [:headers "Content-Type"] "application/json"))))

(defn- record-request [method uri status time]
  (log "request %s %s %d (%dms)" method uri status time)
  (let [filtered_method (subs (str method) 1)
        filtered_uri (string/replace (subs uri 1) #"[^\w.-]" "_")]
    (metric (format "api.request.%s.%s.time" filtered_method filtered_uri) time)
    (metric (format "api.request.%s.%d" filtered_method status) 1)))

(defn wrap-request-logging [handler]
  (fn [request]
    (let [start (System/currentTimeMillis)
          response (handler request)
          finish (System/currentTimeMillis)
          time (- finish start)]
      (record-request (:request-method request) (:uri request) (:status response) time)
      response)))