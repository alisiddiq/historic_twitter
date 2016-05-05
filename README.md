# historic-twitter
The twitter api is restricted to maximum two weeks worth of data for queries. Therefore I have developed another way, which just mimics the browser behaviour and gets all the data from the specified date. If it is accessible by browser, it is accessible by this method, the only difference is that you won't have to do the endless scrolling.


## Usage
1. Load the repl
2. Then load the namespace in the repl

 ```Clojure
 (use 'historic_twitter.twitter)
 (in-ns 'historic_twitter.twitter)
 ```
 
3. Then call the function, specifying the query, location of the target csv and the since-date (defaults to 15 days ago, if not provided)
 ```Clojure
 (get-tweets "$CNA" "CNA.csv" "2008-01-01")
 ```
