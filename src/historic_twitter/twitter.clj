(ns historic_twitter.twitter
  (:require [clj-time.core :as t]
            [clj-time.format :as f]
            [clj-time.coerce :as c]
            [clojure.data.csv :as csv]
            [clojure.string :as string]
            [clj-http.client :as client]
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
                   :cookies      cookies}
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

(defn tweet-request-with-id
  "Can be used to continue a disrupted process of extracting tweets, new-id is the latest from the processes-ids.txt file, query parameter should be exactly the same as the original request"
  [new-id since-date query target-csv-location]
  (let [since-date (if (= java.lang.String (type since-date))
                     (or (tc #(f/parse (f/formatter "yyyy-MM-dd") since-date)) (t/ago (t/days 15)))
                     since-date)
        other-base-url "https://twitter.com/i/search/timeline"
        get-other-params-fn (fn [id]
                              {:f "tweets" :vertical "news" :q query :src "typd" :max_position id :include_available_features "1" :include_entities "1" :reset_error_state "false"})
        initial-tweets-map {:max_position new-id :tweets [{:date-time (t/now)}]}
        create-tweet-vector (fn [x] [(:date-formatted x) (:user x) (:tweet x) (:likes x) (:re-tweets x)])]
    (with-open [out-file (clojure.java.io/writer target-csv-location)]
      (loop [td initial-tweets-map]
        (let [last-date (:date-time (last (:tweets td)))]
          (if (t/after? last-date since-date)
            (let [tweets-data (extract-tweets-from-html (:body (http-get other-base-url :query-params (get-other-params-fn (:max_position td)) :content-type :json)))
                  new-tweets-vector (mapv #(create-tweet-vector %) (:tweets tweets-data))]
              (csv/write-csv out-file new-tweets-vector)
              (spit "processed-ids.txt" [(:max_position tweets-data)] :append true)
              (recur tweets-data)))))
      )))

(defn get-tweets
  "Main function to call, with query, location of the output csv (e.g. out/tweets.csv) and since-date in format yyyy-mm-dd (by default it will go back 15 days)"
  [query target-csv-location & [since-date]]
  (let [since-date  (or (tc #(f/parse (f/formatter "yyyy-MM-dd") since-date)) (t/ago (t/days 15)))
        first-base-url "https://twitter.com/search"
        initial-query-params {:f "tweets" :vertical "news" :q query :src "typd"}
        first-request (http-get first-base-url :query-params initial-query-params)
        initial-tweets-map (extract-tweets-from-html (:body first-request))
        create-tweet-vector (fn [x] [(:date-formatted x) (:user x) (:tweet x) (:likes x) (:re-tweets x)])
        tweets-vector (mapv #(create-tweet-vector %) (:tweets initial-tweets-map))
        csv-vector (vec (concat [["Date-Time" "Username" "Tweet" "Likes" "Retweets"]] tweets-vector))]
    (with-open [out-file (clojure.java.io/writer target-csv-location)]
      (csv/write-csv out-file csv-vector)
      (spit "processed-ids.txt" [(:max_position initial-tweets-map)]))
    (tweet-request-with-id (:max_position initial-tweets-map) since-date query target-csv-location)
    ))