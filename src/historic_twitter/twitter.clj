(ns historic_twitter.twitter
  (:require [clj-time.core :as t]
            [clj-time.format :as f]
            [clj-time.coerce :as c]
            [clojure.data.csv :as csv]
            [clojure.string :as string]
            [clj-http.client :as client]
            [clj-http.cookies :as cookies]
            [spyscope.core :as spy]
            [net.cgrand.enlive-html :as html]
            ))


(defn http-get
  [url & {:keys [query-params content-type cookies]}]
  (client/request {:url          url
                   :method       :get
                   :query-params query-params
                   :content-type content-type
                   :as           content-type
                   :cookie-store cookies}
                  )
  )

(defn tc [function]
  (try
    (function)
    (catch Exception e nil)))

(defn extract-data-for-one-tweet
  "Used to extract information for one tweet from html which has already been parsed via enlive"
  [parsed-html]
  (let [username (string/join "" (html/select parsed-html [:span.username.js-action-profile-name html/text-node]))
        tweet-string (string/replace (string/join "" (html/select parsed-html [:div.js-tweet-text-container :> :p html/text-node])) "\n" " ")
        re-tweets (first (:content (first (html/select parsed-html [:button.ProfileTweet-actionButton.js-actionButton.js-actionRetweet :> :div.IconTextContainer :> :span :> :span]))))
        likes (first (:content (first (html/select parsed-html [:button.ProfileTweet-actionButton.js-actionButton.js-actionFavorite :> :div.IconTextContainer :> :span :> :span]))))
        date-time (tc #(c/from-long (read-string (-> (first (:content (first (html/select parsed-html [:a.tweet-timestamp.js-permalink.js-nav.js-tooltip])))) :attrs :data-time-ms))))
        formatted-date-time (tc #(f/unparse (f/formatters :date-hour-minute) date-time))
        ]
    {:date-time date-time :date-formatted formatted-date-time :user username :tweet tweet-string :likes likes :re-tweets re-tweets}
    )
  )

(defn extract-tweets-from-html
  "Uses raw html body to extract all tweets from that page"
  [response-body]
  (let [html-response? (= java.lang.String (type response-body))
        html (if html-response?
               (html/html-snippet response-body)
               (html/html-snippet (:items_html response-body)))
        tweets-data (html/select html [:div.content])
        max-tweet-id (or
                       (:min_position response-body)
                       (-> (first (html/select html [:div.stream-container])) :attrs :data-max-position))
        extracted-tweets (vec (doall (pmap #(extract-data-for-one-tweet %) tweets-data)))
        ]
    {:max_position max-tweet-id :tweets extracted-tweets}
    ))

(def cookie-store (cookies/cookie-store))

(defn get-last-id
  "Reads last tweet id from the text file, used to start a disrupted process"
  [& processed-ids-path]
  (let [processed-ids-path (or processed-ids-path "processed-ids.txt")
        processed-data (read-string (str "[" (slurp processed-ids-path) "]"))
        last-id (first (last processed-data))
        ]
    last-id
    )
  )

(defn get-tweets-map
  "Returns a map with parsed tweets data"
  [query last-tweet-id top-flag]
  (let [url "https://twitter.com/i/search/timeline"
        main-params {:vertical "news" :q query :src "typd" :include_available_features "1" :include_entities "1" :max_position last-tweet-id :reset_error_state "false"}
        query-params (if top-flag main-params (assoc main-params :f "tweets"))
        tweet-data (extract-tweets-from-html (:body (http-get url :query-params query-params :cookies cookie-store :content-type :json)))
        last-date (:date-time (last (:tweets tweet-data)))]
    (if (nil? last-date)
      (do (spit "processed-ids.txt" [(:max_position tweet-data)] :append true)
          (throw (Exception. "Scroll did not return any results")))
      (do
        (spit "processed-ids.txt" [(:max_position tweet-data)] :append true)
        tweet-data)
      )))

(defn try-twitter-n-times
  "A try and catch wrapper around the get-tweets-map function, keeps on tryin n times if an exception is thrown"
  [n sleep-time query top-flag]
  (if-not (zero? n)
    (try
      (get-tweets-map query (get-last-id) top-flag)
      (catch Exception e
        (do (println (str "Exception found: " (.getMessage e) ", retrying after " (/ sleep-time 1000) " seconds....."))
            (Thread/sleep sleep-time)
            (try-twitter-n-times (dec n) (+ sleep-time 5000) query top-flag)
            )))
    (println "Failed after re-attempts :( exiting...")
    ))

(defn tweet-request-with-id
  "Can be used to continue a disrupted process of extracting tweets, new-id is the latest from the processes-ids.txt file, query parameter should be exactly the same as the original request"
  [new-id since-date query target-csv-location & {:keys [top retry-attempts]}]
  (let [since-date (if (= java.lang.String (type since-date))
                     (or (tc #(f/parse (f/formatter "yyyy-MM-dd") since-date)) (t/ago (t/days 15)))
                     since-date)
        retry-attempts (or retry-attempts 10)
        initial-tweets-map {:max_position new-id :tweets [{:date-time (t/now)}]}
        create-tweet-vector (fn [x] [(:date-formatted x) (:user x) (:tweet x) (:likes x) (:re-tweets x)])]
    (with-open [out-file (clojure.java.io/writer target-csv-location)]
      (loop [td initial-tweets-map
             count 1]
        (let [last-date (:date-time (last (:tweets td)))]
          (if (t/after? last-date since-date)
            (let [tweets-data (try-twitter-n-times retry-attempts 10000 query top)
                  new-tweets-vector (mapv #(create-tweet-vector %) (:tweets tweets-data))]
              (csv/write-csv out-file new-tweets-vector)
              (println (str "Scroll #" count ", last Date " (:date-formatted (last (:tweets td)))))
              (recur tweets-data (+ count 1)))
            (println "All tweets extracted for the given period, exiting...")
            ))
        ))))

(defn get-tweets
  "Main function to call, with query, location of the output csv (e.g. out/tweets.csv) and since-date in format yyyy-mm-dd (by default it will go back 15 days). To get results from top tweets timeline, also add :top true"
  [query target-csv-location & {:keys [since-date top retry-attempts]}]
  (let [top (or top false)
        retry-attempts (or retry-attempts 10)
        since-date (or (tc #(f/parse (f/formatter "yyyy-MM-dd") since-date)) (t/ago (t/days 15)))
        first-base-url "https://twitter.com/search"
        main-map {:vertical "news" :q query :src "typd"}
        initial-query-params (if top main-map (assoc main-map :f "tweets"))
        first-request (http-get first-base-url :query-params initial-query-params :cookies cookie-store)
        initial-tweets-map (extract-tweets-from-html (:body first-request))
        create-tweet-vector (fn [x] [(:date-formatted x) (:user x) (:tweet x) (:likes x) (:re-tweets x)])
        tweets-vector (mapv #(create-tweet-vector %) (:tweets initial-tweets-map))
        csv-vector (vec (concat [["Date-Time" "Username" "Tweet" "Likes" "Retweets"]] tweets-vector))]
    (with-open [out-file (clojure.java.io/writer target-csv-location)]
      (csv/write-csv out-file csv-vector)
      (spit "processed-ids.txt" [(:max_position initial-tweets-map)]))
    (tweet-request-with-id (:max_position initial-tweets-map) since-date query target-csv-location :top top :retry-attempts retry-attempts)
    ))
