(ns historic-twitter.core
  (:require [historic_twitter.twitter :as twitter])
  (:gen-class :main true))

(defn -main
   [& args]
  (let [args-length (count args)]
    (case args-length
      0 (println "Usage  <QUERY> <CSV LOCATION> <optional SINCE-DATE> <optional TOP timeline flag> <optional RETRY-ATTEMPTS>")
      1 (println "Usage  <QUERY> <CSV LOCATION> <optional SINCE-DATE> <optional TOP timeline flag> <optional RETRY-ATTEMPTS>")
      2 (twitter/get-tweets (nth args 0) (nth args 1))
      3 (twitter/get-tweets (nth args 0) (nth args 1) :since-date (nth args 2))
      4 (twitter/get-tweets (nth args 0) (nth args 1) :since-date (nth args 2) :top (read-string (nth args 3)))
      5 (twitter/get-tweets (nth args 0) (nth args 1) :since-date (nth args 2) :top (read-string (nth args 3)) :retry-attempts (read-string (nth args 4)))
      )))
