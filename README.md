#TF-Pundit
============
Author : **Karthik Anantha Padmanabhan**

This will be a set of tools that can be used to analyze live football(soccer) matches using twitter as the medium. The tasks include event summarization , automated player ratings and some more that will added.

## Requirements

* Version 1.6 of the Java 2 SDK (http://java.sun.com)

## Building the system from source

tf-pundit uses SBT (Simple Build Tool) with a standard directory
structure.  

  $ ./build update compile
  
  

##Summarization

To run key in words that are relvant to a soccer game in "terms to be summarized"
Replace players with the player whose performance is to be monitored

$ bin/fanalyst run footballTwitter.twitter.GameAnalyst -t "terms to be summarized" -p "players"

Example : bin/fanalyst run footballTwitter.twitter.GameAnalyst -t "milan" "barca" "acm" "barca" -p "messi" "balotelli" "xavi"

The current implementation produces text files having the summary in rtSummary.txt
and the sentiment associated with the players in scores.txt. The files are updates every minute



