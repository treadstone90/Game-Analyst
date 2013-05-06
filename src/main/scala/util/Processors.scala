package footballTwitter.twitter
import scala.collection.JavaConversions._;
import org.apache.commons.math3.linear.ArrayRealVector
import scala.io.Source
import chalk.lang.eng.Twokenize
import footballTwitter.twitter.MessageStore._
import footballTwitter.util.Tagger
import akka.util._
import akka.actor._;
import footballTwitter.util.English
import footballTwitter.util.Vector
import footballTwitter.util.JaccardSimilarity
import footballTwitter.util.CosineSimilarity



/** The Summarizer receives a set of tweets from the Aggregator and summarizer the
 * It produces one tweet as the summary - so the summarization is extractive
 */

class Summarizer extends Actor with ActorLogging {
  

  def receive = {
    case Payload(tweetList: IndexedSeq[String]) => {
      println("Now summarizing tweets....");
      val filteredTweets = summarizationFilter(tweetList.toIndexedSeq);
      //tweetList.foreach(println)
      println("--------------------");
      //filteredTweets.foreach(println);
      println("@@@@@@@@@@@@@@@@@@@@@@");
      val summaries = Summarizer(filteredTweets)
        .map(tweet => tweet._1)

      summaries.foreach(println);
      context.actorFor("../Renderer") ! Summary(summaries);

    }
  }

  def summarizationFilter(tweetList: IndexedSeq[String]): IndexedSeq[String] = {
    val taggedTokens = tweetList
      .map(tweet => Tagger(tweet))

    val filteredTweets = tweetList.filter(tweet => !English.isImperative(tweet) && English.isSafe(tweet) && !English.isPersonal(tweet))
    filteredTweets
  }
}

/* The Perf Analyzer rates the players. 
 * It passes the tweets to the sentiment Analyzer which returns the score for every player
 */

class PerfAnalyzer(players: List[String]) extends Actor with ActorLogging {

  def receive = {
    case Payload(tweetList: IndexedSeq[String]) => {

      //println(tweetList)
      val playersScore: Map[String, Double] = SentimentAnalyzer(players, tweetList.toIndexedSeq);
      context.actorFor("../Renderer") ! PlayerScores(playersScore);
      //println(playersScore)
    }
  }
}






/** ANn object that summarizes a given list oftweets */
object Summarizer {

  def apply(tweets: IndexedSeq[String]) = {
    val tokenizedTweets = tweets.map(Twokenize(_))

    val vocabulary = tokenizedTweets.flatten
      .toSet
      .toIndexedSeq

    val tweetMaps = tokenizedTweets
      .map(tweet => tweet.groupBy(x => x)
        .mapValues(x => x.length)
        .withDefaultValue(0))

    val IDFMap = IDF(vocabulary, tweetMaps)

    val featureVectors = tweetMaps
      .map(tweetMap => Vector(vocabulary, tweetMap, IDFMap))

    val rankedTweets = rank(tweets, featureVectors);
    rankedTweets.reverse;

  }

  def rank(tweets: IndexedSeq[String], tweetVectors: IndexedSeq[ArrayRealVector]) =
    {
      val scoredTweets = scala.collection.mutable.ArrayBuffer[(String, Double)]()
      val similarityScore = tweetVectors.map { tweet =>
        CosineSimilarity.score(tweet, tweetVectors)
      }

      var index = 0;
      tweets.foreach { tweet =>
        val score = similarityScore(index) + POSScore(tweet);
        index += 1;
        scoredTweets += Tuple2(tweet, score)
      }

      val rankedTweets = scoredTweets.sortBy(x => x._2).takeRight(5);
      rankedTweets
    }

  def POSScore(tweet: String) = {
    val tagged = Tagger(tweet)

    val count = tagged.count(taggedToken => taggedToken.tag == "V" || taggedToken.tag == "^"
      || taggedToken.tag == "Z")

    count * 1.0 / Twokenize(tweet).length

  }
  def IDF(vocabulary: IndexedSeq[String], documents: IndexedSeq[Map[String, Int]]): Map[String, Double] =
    {
      val IDFMap = scala.collection.mutable.Map[String, Double]()
        .withDefaultValue(0);

      vocabulary.foreach { word =>
        val df = documents.count(document => document.contains(word))
        IDFMap(word) = Math.log(documents.size / df);
      }

      IDFMap.toMap

    }

}


/** AN object that gets the sentiment of players */
object SentimentAnalyzer {
  def apply(players: List[String], tweetList: IndexedSeq[String]) =
    {
      val playerScores = scala.collection.mutable.Map[String, Double]().withDefaultValue(0.5);
      players.foreach { player =>
        val playerTweets: Seq[String] = tweetList.filter(_.contains(player));
      //  println(player + ":")
       // playerTweets.foreach(println)
        val tokenizedTweets = playerTweets.map(tweet => Twokenize(tweet))
        val tweetsSentiment = tokenizedTweets.map { tweet =>
          val positive = tweet.count(English.posWords)
          val negative = tweet.count(English.negWords)

          if (positive == negative) 0.5
          else if (positive > negative) 1
          else 0
        }

        if (tweetsSentiment.length > 0)
          playerScores(player) = tweetsSentiment.sum / tweetsSentiment.length;
      }

      playerScores.toMap
    }
}