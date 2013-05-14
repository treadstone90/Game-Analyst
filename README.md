#TF-Pundit
============
Author : **Karthik Anantha Padmanabhan**

This will be a set of tools that can be used to analyze live football(soccer) matches using twitter as the medium. The tasks include event summarization , automated player ratings and some more that will added.

## Requirements

* Version 1.6 of the Java 2 SDK (http://java.sun.com)

## Building the system from source

TF-pundit uses SBT (Simple Build Tool) with a standard directory
structure.  

  `./build update compile`


##Summarization and Player Performance Analyzer

These are the two components of the system that have currently been implemented. To use this, first identify the keywords
associated with a particular game. For example, for a game between `AC Milan and Catania` the keywords maybe `acm, acmilan,catania,acmcatania,milancatania`

To run TF-Pundit :

`$ bin/fanalyst run footballTwitter.twitter.GameAnalyst -t "game keywords" -p "players to follow"`

Replace `players to follow` with the player whose performance is to be monitored

Example : 

`bin/fanalyst run footballTwitter.twitter.GameAnalyst -t "milan" "barca" "acm" "barca" -p "messi" "balotelli" "xavi"`

The above command can be used to follow a game betweebn AC Milan and FC Barcelona. The players who will be analyzed are
`messi balotelli and xavi`

The current implementation produces text files having the summary in `rtSummary.txt`
and the sentiment associated with the players in `scores.txt`. 

The tweets location(geo-coordinates) are also written to `location.txt`



