package footballTwitter.twitter

import twitter4j._
import scala.collection.JavaConversions._;
import chalk.lang.eng.Twokenize
import akka.util._
import akka.actor._;
import footballTwitter.util._
import footballTwitter.twitter._
import org.rogach.scallop._
import sys.process._
import scala.io.Source
import MessageStore._
import scala.collection.mutable.ArrayBuffer

/** An Object that consists of all the messages that will be passed in this system*/

object MessageStore {
  object Locate
  object Shutdown
  object Start
  object Report
  case class Label(x: String, count: Int, minute: Int)
  case class Rate(x: Int)
  case class Stream(terms: Array[String])
  case class FullStatus(status: Status, JSON: String)
  case class TaggedTweet(tag: String, tweet: String)
  case class Payload(tweetList: IndexedSeq[String])
  case class Summary(summaries: IndexedSeq[String])
  case class PlayerScores(playerScores: Map[String, Double])
}

/** The Actor System TF-Pundit. The "supervisor" actor - Manager is initialized here */

object GameAnalyst {

  import MessageStore._

  def main(args: Array[String]) {
    val system = ActorSystem("Analyst");
    val conf = new Conf(args);
    val terms = conf.terms().toArray
    //	val ids = conf.ids()
    val players = conf.players();

    val manager = system.actorOf(Props(new Manager(players)), name = "Manager");
    manager ! Stream(terms);

  }
}

/** The Manager Actor acts as a supervisor for the entire system 
 * Its child actors are : 
 *	1) Summarizer
 *	2) Sentiment Analyzer
 *	3) Renderer
 * @param the list of players to be analyzed.
*/
class Manager(players: List[String]) extends Actor with ActorLogging {
  import GameAnalyst._;
  import MessageStore._

  val streamManager = context.actorOf(Props[StreamManager], name = "StreamManager");
  val summarizer = context.actorOf(Props[Summarizer], name = "Summarizer");
  val sentiAnalyzer = context.actorOf(Props(new PerfAnalyzer(players)), name = "PerfAnalyzer");
  val renderer = context.actorOf(Props[Renderer], name = "Renderer");

  def receive = {
    case Stream(terms) => streamManager ! Stream(terms)

  }
}

/** The Renderer class will ideally format the output. 
 * The currnet implementation simply writes the output to a file. 
 * In a more ambitious implementation this can be used to pipe the output to D3.js
 */

class Renderer extends Actor with ActorLogging {
  import java.io.FileWriter
  import footballTwitter.util.JaccardSimilarity
  import scala.util.control.Breaks._

  val summary = scala.collection.mutable.ArrayBuffer[String]();
  val wrSum = new FileWriter("rtSummary.txt");
  val wrSenti = new FileWriter("scores.txt");

  def receive =
    {

      case Summary(candidates: IndexedSeq[String]) => {
        println("1got payload");

        if (summary.length == 0 && candidates.length > 0) {
          summary += candidates.head;
          wrSum.write(candidates.head + "\n")

        } else {
          val newCandidates = candidates.filterNot(hasDuplicate)
          if (newCandidates.length > 0) {
            summary += newCandidates.head
            wrSum.write(newCandidates.head + "\n")
          }
        }
        wrSum.flush

      }

      case PlayerScores(playerScores: Map[String, Double]) => {
        playerScores.foreach {
          case (key, value) =>
            wrSenti.write(key + ";" + value + "\n");
            wrSenti.flush
        }
      }
    }

  def hasDuplicate(candidate: String) = {
    val similarityScores = summary.map(tweet => JaccardSimilarity(candidate, tweet))
    val duplicates = similarityScores.count(score => score > 0.8);

    if (duplicates > 0) true else false

  }

  //override def postStop = wrSum.close ; wrSenti.close
}

/** The Stream Manager is reponsible for streaming the tweets to other actors
 * Its child actors are Locator, Labeller and Selector. 
 */
class StreamManager extends Actor with ActorLogging with TermFilter {
  import GameAnalyst._;
  import MessageStore._


  val streamer = new Streamer(context.self);
  val locator = context.actorOf(Props[Locator], name = "Locator")
  val labeller = context.actorOf(Props[Labeller], name = "Labeller")
  val selector = context.actorOf(Props[Selector], name = "Selector")
  var tweetCount = 0;
  var fileName = "";

  /* Receives messages from the Manager and processes them
   * Message 1) Stream - start streaming tweets
   * Message 2) Shutdown - stop streaming tweets
   * Message 3) FullStatus - Passes messages to its worker actors
   */

  def receive = {
    case Stream(terms) => streamer.stream.filter(getQuery(terms))
    case Shutdown => {
      labeller ! Shutdown
      streamer.stream.shutdown;
    }

    case fullStatus: FullStatus => {

      tweetCount = tweetCount + 1
      labeller ! fullStatus
      selector ! fullStatus
      locator ! fullStatus

    }
  }

}

/** The Selector Actor is reponsible for weeding out clearly irrelvant tweets.
 * It also tags the tweets with a timestamp. 
 * Its child actor is Aggregator, to which it passes tweets
 */
class Selector extends TweetWriter("tweets.txt") with Actor with ActorLogging {
  import GameAnalyst._
  import MessageStore._;
  import footballTwitter.util.Tweet._;
  import footballTwitter.util.English;

  var tag: String = "Normal";
  var count: Int = 0;
  var gameMinutes = 0;
  val aggregator = context.actorOf(Props[Aggregator], name = "Aggregator")

  /* Receives messages from the Manager and processes them
   * Message 1) Label - Label the tweets 
   * Message 2) Shutdown - stop actor
   * Message 3) FullStatus - Filter and Tag tweets
   */  

  def receive = {
    case fullstatus: FullStatus =>
      {
        if (Tweet.getLanguage(fullstatus) == "en" && !fullstatus.status.isRetweet && fullstatus.status.getMediaEntities.length == 0) {
          val normalizedTweet = English.removeNonLanguage(Tweet.normalize(fullstatus.status.getText));

          val entry: String = gameMinutes + ":" + count + ":" + tag + "~~~~~~~~" + normalizedTweet;

          if (Math.random <= 0.7 && Twokenize(normalizedTweet).length > 3) {
            write(entry);
            aggregator ! TaggedTweet(tag, normalizedTweet);
          }

        }
      }

    case Label(label: String, counter: Int, minute: Int) =>
      tag = label; count = counter; gameMinutes = minute

    case Shutdown => {
      context.stop(self)
    }

  }
  def write(entry: String) {
    wr.write(entry + "\n");
    wr.flush
  }
  override def postStop = closeWriter
}

/** The Aggregator class is used to collect all tweets from a one minute interval and package them together
 * The packaged tweets are then passed to the text processing elements - Summarizer and Performance Analzer
 */

class Aggregator extends Actor with ActorLogging {
  val tweetsPot = scala.collection.mutable.ArrayBuffer[String]()
  var prevTag = "";
  
  /* Receives messages from the Manager and processes them
   * Message 1) TaggedTweet - Collect all tweets in a "pot" , package them and send the payload to Summarizer and Performance Analyzer
   */
  def receive = {
    case TaggedTweet(tag: String, tweet: String) =>
      {
        if (tag == prevTag || prevTag == "")
          tweetsPot.append(tweet);
        else {
          println("sending for processing.....")
          val payload = tweetsPot.map(x => new String(x))
          context.actorFor("../../../Summarizer") ! Payload(payload)
          context.actorFor("../../../PerfAnalyzer") ! Payload(payload)
          tweetsPot.clear
        }

        prevTag = tag

      }
  }
}

/** The Labeller class counts the number of tweets received and sends a label to the Selector every minute */
class Labeller extends Actor with ActorLogging {
  import GameAnalyst._
  import scala.concurrent.duration._
  import scala.concurrent.ExecutionContext
  import MessageStore._

  implicit val ec = ExecutionContext.Implicits.global

  context.system.scheduler.schedule(1000.millis, 60000.millis, context.self, Report)
  var minute = 0;
  var counter = 0;
  val threshold = 500;
  var current = 0;
  var old = 0;
  var switch = 0;

  /** Recieve the following messages 
   * 1) fullStatus : incremnt counter 
   * 2) Report : Send Label to Selector with the  the number of tweets in interval and timestamp
   */

  def receive = {

    case fullStatus: FullStatus => {
      counter = counter + 1;

    }

    case Report =>
      {
        minute += 1;
        old = current;
        current = counter;

        println("count is" + counter);

        if (current - old > threshold) {
          switch = switch + 1
          context.actorFor("../Selector") ! Label("Burst" + "-" + minute, current, minute)
        } else
          context.actorFor("../Selector") ! Label("Normal" + "-" + minute, current, minute)

        counter = 0;
      }
  }
}

/** An abstract class that writes to a file 
 *  @param the list of players to be analyzed.
 */
abstract class TweetWriter(fileName: String) {
  import java.io.FileWriter
  val wr = new FileWriter(fileName)
  /** Write text file specified in fileName
   * @param : text to write
   * return None
   */  
  def write(entry: String)

  def closeWriter() = wr.close;

}

/** The Locator class used to get the geoCordinates of a tweet 
 *
*/

class Locator extends TweetWriter("location.txt") with Actor with ActorLogging {
  import MessageStore._

  import footballTwitter.util.Tweet._;

  val placeMap = scala.collection.mutable.Map[String, Int]().withDefaultValue(0);
  val geoNames = new GeoNames("treadstone90")

  /** Receives the following Messages
   * 1) fullStatus : geoTag the tweets
   */

  def receive = {
    case fullStatus: FullStatus =>{
      val location = Option(fullStatus.status.getGeoLocation) match {
        case Some(geo:GeoLocation) => Some(LocationConfidence(geo.getLatitude,geo.getLongitude,1.0))
        case None => 
          Option(fullStatus.status.getUser) match {
            case Some(user:User) => geoNames.locatePlaceByName(fullStatus.status.getUser.getLocation)
            case None => None
        }
      }
      location match {
        case Some(loc) => write(loc.latitude+";"+loc.longitude + "\n");
        case None => 
      } 
    }
  }

  def write(entry: String) {
    wr.write(entry + "\n");
    wr.flush
  }

  override def postStop = closeWriter

}

/** Conf class is used to handle the command line arguements */

class Conf(arguements: Seq[String]) extends ScallopConf(arguements) {
  banner("""
			Twitter Soccer Streaming
	For usagse see below :

	-t --terms <arg>	The terms associated with the game that will be streamed
						For example if you want to stream a game between FcBarcelona and AC Milan
						then possible terms can be "fcb" "acmilan" "acm" "barcelona"

	-p --players <arg>  The players whose ratings have to  be monitored in real time
 
		""")

  version(""" Version 0.2 2013 """);
  val terms = opt[List[String]]("terms", descr = "The terms that you need to stream");
  val players = opt[List[String]]("players", descr = "The List of players who u want to follow");

}

