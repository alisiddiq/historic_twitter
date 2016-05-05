(defproject historic_twitter "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url  "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [enlive "1.1.1"]
                 [spyscope "0.1.5"]
                 [clj-webdriver "0.7.2"]
                 [clj-http "2.0.1"]
                 [org.jsoup/jsoup "1.8.1"]
                 [org.apache.httpcomponents/httpclient "4.5"]
                 [org.clojure/data.csv "0.1.3"]]
  :main ^:skip-aot historic_twitter.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})
