package footballTwitter.twitter

import scala.collection.JavaConversions._;
import footballTwitter.util.SimpleTokenizer
import org.apache.commons.math3.linear.ArrayRealVector
import scala.io.Source
import footballTwitter.util.Tagger
import footballTwitter.util.TaggedToken
import chalk.lang.eng.Twokenize;
import footballTwitter.util.English;
import footballTwitter.util.TweetInfo;



/** A Sentiment Analyzer object for Demonstration and offline vevaluation */

object SentimentAnalyzerOffline
{
	def main(args:Array[String])
	{
		val conf =  SentimentOpts(args);
		val tweetFile = conf.tweetFile();
		val playerNames = conf.playerNames();
		//println("hi");
		val method = conf.method();

		val tweetsInfo = io.Source.fromFile(tweetFile).getLines
		.toIndexedSeq
		.map{line=>
			
		//	println(line);
			val tokens = line.split("~~~~~~~~");
			val tweet= tokens(1);
			val filteredTweet = Filter.stripTweets(tweet);
			val minute = tokens(0).split(":")(0);
			val tag = tokens(0).split(":")(2)
			new TweetInfo(minute.toInt,tag,tweet,filteredTweet);
		}

			val tweetGroups=tweetsInfo.groupBy(tweetInfo=> tweetInfo.tag)
			
			val orderedEvents = tweetGroups.keys.toIndexedSeq
			.sortBy(x=> (x.split("-")(1).toInt,x.split("-")(0)))

			val playerScores = scala.collection.mutable.Map[String,scala.collection.mutable.Map[String,Option[Double]]]()

			playerNames.foreach{ player =>
				val eventMap = scala.collection.mutable.Map[String,Option[Double]]().withDefaultValue(Some(0.0));
				
				orderedEvents.foreach{event =>
				
				val tweets:Seq[String] =  tweetGroups(event)
				.map(tweetInfo=> tweetInfo.tweet)
				.filter(_.contains(player))

				val tokenizedTweets = tweets.map(tweet=> Twokenize(tweet))

					val tweetsSentiment=tokenizedTweets.map{tweet=> 
						val positive = tweet.count(English.posWords)
						val negative = tweet.count(English.negWords)

						if(positive == negative) 0.5
						else if(positive > negative) 1
						else 0 
							
					}
					
				eventMap(event) = if(tweetsSentiment.length >0) Some(tweetsSentiment.sum/tweetsSentiment.length); else None
				}
				playerScores(player) = eventMap
			}	

		playerNames.foreach{player =>
			val eventMap = playerScores(player);

			val eventScores = orderedEvents.flatMap(event => eventMap(event));

			println(player + " : rating is " + (5*(eventScores.sum)/eventScores.length) +1 ); 

		}
	}

}






/* Objetc for handling tje commnad line arguements */
object SentimentOpts {
import org.rogach.scallop._;

  def apply(args:Array[String]) = new ScallopConf(args){


	val tweetFile = opt[String]("file",descr="The aggregated tweet file");
	val playerNames= opt[List[String]]("player",descr = "The players whose score is to be foudn out")	
	val method = opt[String]("method",default = Some("lexicon"),descr = "The type of sentiment analayzer");

  }
}	


object Filter {
	def stripTweets(tweet:String)= 
	{
		val filtered = English.removeNonLanguage(tweet);
		filtered
	}	
}
