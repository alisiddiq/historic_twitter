# historic_twitter
The twitter api is restricted to maximum two weeks worth of data for queries. This method overcomes this restriction by using http libraries, and mimicking the scrolling behaviour on twitter. If data is accessible by browser, it is accessible by this method, the only difference is that you won't have to do the endless scrolling.

## Usage from REPL
1. Load the repl
2. Then load the namespace in the repl

 ```Clojure
 (use 'historic_twitter.twitter)
 (in-ns 'historic_twitter.twitter)
 ```
 
3. Then call the function, specifying the query, location of the target csv and the since-date (defaults to 15 days ago, if not provided)
 ```Clojure
 (get-tweets "$CNA" "CNA.csv" :since-date "2008-01-01" :top true) ;To get results from Top timeline
 or
 (get-tweets "$CNA" "CNA.csv" :since-date "2008-01-01") ;To get results from Live timeline
 ```

 ## Usage from JAR file
 ```Java
 java -jar historic_twitter.jar <QUERY> <CSV LOCATION> <optional SINCE-DATE> <optional TOP timeline flag>
 ```
