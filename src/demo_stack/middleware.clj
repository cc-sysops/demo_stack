(ns demo_stack.middleware
  (:require [clojure.tools.logging :as log]
            [clojure.string :as string]
            [cheshire.core :as json]
            [demo_stack.graphite :as graphite]))

(defn wrap-exception-logging [handler]
  (fn [request]
    (try
      (handler request)
      (catch Exception error
        (log/error error "failed to complete a request")
        (throw error)))))

(defn wrap-failsafe [handler]
  (fn [request]
    (try
      (handler request)
      (catch Exception error
        {:status 500
         :body (json/generate-string {:error (str error)})}))))

(defn wrap-content-type [handler]
  (fn [request]
    (let [response (handler request)]
      (assoc-in response [:headers "Content-Type"] "application/json"))))

(defn- record-request [method uri status time]
  (log/infof "request %s %s %d (%dms)" method uri status time)
  (let [filtered_method (subs (str method) 1)
        filtered_uri (string/replace (subs uri 1) #"[^\w.-]" "_")]
    (graphite/store (format "api.request.%s.%s.time" filtered_method filtered_uri) time)
    (graphite/store (format "api.request.%s.%s.%d" filtered_method filtered_uri status) 1)))

(defn wrap-request-logging [handler]
  (fn [request]
    (let [start (System/currentTimeMillis)
          response (handler request)
          finish (System/currentTimeMillis)
          time (- finish start)]
      (record-request (:request-method request) (:uri request) (:status response) time)
      response)))
