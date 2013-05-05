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



object MessageStore{
	object Locate // this is from the manager to the person doing the location
	object Shutdown // this is from the manager
	object Start // this is for the manager
	object Report // from counter to counter
	case class Label(x:String,count:Int,minute:Int) // from Couner to Labeller
	case class Rate(x:Int) // from counter to manager
	case class Stream(terms:Array[String])
	case class FullStatus(status:Status,JSON:String)
	case class TaggedTweet(tag:String,tweet:String)
	case class Payload(tweetList:IndexedSeq[String])
	case class Summary(summaries:IndexedSeq[String])
	case class PlayerScores(playerScores: Map[String,Double])
}


// The Actor System

object GameAnalyst
{

	import MessageStore._

	def main(args:Array[String]) {
	val system = ActorSystem("Analyst");
		val conf  = new Conf(args);
		val terms = conf.terms().toArray
	//	val ids = conf.ids()
		val players = conf.players();

				
		val manager = system.actorOf(Props(new Manager(players)),name ="Manager");
		manager ! Stream(terms);
		
	}
}


class Manager(players:List[String]) extends Actor with ActorLogging {	
	import GameAnalyst._;	
	import MessageStore._

	val streamManager = context.actorOf(Props[StreamManager],name ="StreamManager");
	val summarizer = context.actorOf(Props[Summarizer],name ="Summarizer");
	val sentiAnalyzer = context.actorOf(Props(new SAnalyzer(players)),name ="SAnalyzer");
	val renderer = context.actorOf(Props[Renderer],name ="Renderer");


	def receive ={
		case Stream(terms) => streamManager ! Stream(terms)

	}
}

class Renderer extends Actor with ActorLogging {
	import java.io.FileWriter
	import footballTwitter.util.JaccardSimilarity
	import scala.util.control.Breaks._

	val summary = scala.collection.mutable.ArrayBuffer[String]();
	val wrSum = new FileWriter("rtSummary.txt");
	val wrSenti = new FileWriter("scores.txt");

	def receive =
	{

		case Summary(candidates:IndexedSeq[String]) => {
			println("1got payload");
			
			if(summary.length ==0 && candidates.length >0)
			{
				summary += candidates.head;
				wrSum.write(candidates.head + "\n")
				
			}
			else
			{
				val newCandidates = candidates.filterNot(hasDuplicate)
				if(newCandidates.length > 0) {
					summary += newCandidates.head
					wrSum.write(newCandidates.head +"\n")
				}
			}
			wrSum.flush
			
		}

		

		case PlayerScores(playerScores: Map[String,Double]) => {
			playerScores.foreach{ case(key,value) => 
				wrSenti.write(key + ";" + value +"\n");
				wrSenti.flush
			}
		}
	}

	def hasDuplicate(candidate:String) ={
		val similarityScores= summary.map(tweet=> JaccardSimilarity(candidate,tweet))
		val duplicates = similarityScores.count(score => score >0.5);
		
		if(duplicates > 0) true else false


	}

	//override def postStop = wrSum.close ; wrSenti.close
}

class StreamManager extends Actor with ActorLogging with TermFilter
{
	import GameAnalyst._;
	import MessageStore._

	val streamer = new Streamer(context.self);
	val locator = context.actorOf(Props[Locator], name ="Locator")
	val labeller = context.actorOf(Props[Labeller], name ="Labeller")
	val selector = context.actorOf(Props[Selector],name ="Selector")
	var tweetCount=0;
	var fileName="";
	
	/*override def preStart ={
		//streamer.stream.sample
	}
*/
	def receive  = {
		case Stream(terms) => streamer.stream.filter(getQuery(terms))
		case Shutdown => {
			labeller ! Shutdown
			streamer.stream.shutdown;
		}
		
		case fullStatus : FullStatus => {

		//println(fullStatus.status.getText)
		tweetCount = tweetCount +1
		
		labeller ! fullStatus
		selector ! fullStatus
		locator ! fullStatus		
		
		}
	}

}


class Selector extends TweetWriter("tweets.txt") with Actor with ActorLogging
{
	import GameAnalyst._
	import MessageStore._;
	import footballTwitter.util.Tweet._;
	import footballTwitter.util.English;
	
	var tag :String = "Normal"; 
	var count:Int = 0;
	var gameMinutes = 0;
	val aggregator = context.actorOf(Props[Aggregator],name ="Aggregator")


	def receive = {
		case fullstatus : FullStatus => 
		{
			if(Tweet.getLanguage(fullstatus) == "en" && !fullstatus.status.isRetweet && fullstatus.status.getMediaEntities.length == 0)
			{
				val normalizedTweet = English.removeNonLanguage(Tweet.normalize(fullstatus.status.getText));

				val entry:String = gameMinutes+":"+count+":"+tag + "~~~~~~~~"+ normalizedTweet;

				
				if(Math.random <=0.7 && Twokenize(normalizedTweet).length > 3)
				{
					write(entry);
					aggregator ! TaggedTweet(tag,normalizedTweet);
				}

			}
		}

		case Label(label:String,counter:Int,minute:Int) => 
			tag = label;count = counter;gameMinutes=minute

		case Shutdown => {
			context.stop(self)
		}
		
	}
	def write(entry:String) 
	{
		wr.write(entry + "\n");
		wr.flush
	}
	override def postStop =closeWriter	
}

class Aggregator extends Actor with ActorLogging
{
	val tweetsPot = scala.collection.mutable.ArrayBuffer[String]()
	//val payload = scala.collection.mutable.ArrayBuffer[String]()
	var prevTag = "";
	def receive ={
		case TaggedTweet(tag:String,tweet:String) => 
		{
			//println("collecting tweets ........");
			if(tag == prevTag || prevTag=="")
				tweetsPot.append(tweet);
			else
			{
				println("sending for processing.....")
				val payload= tweetsPot.map(x=> new String(x))
				context.actorFor("../../../Summarizer") ! Payload(payload)
				context.actorFor("../../../SAnalyzer") ! Payload(payload)
				tweetsPot.clear
			}

			prevTag = tag
			

		}
	}
}

class Summarizer extends Actor with ActorLogging
{
	import footballTwitter.util.English

	def receive ={
		case Payload(tweetList:IndexedSeq[String]) => {
			println("Now summarizing tweets....");
			val filteredTweets = summarizationFilter(tweetList.toIndexedSeq);
			//tweetList.foreach(println)
			println("--------------------");
			//filteredTweets.foreach(println);
			println("@@@@@@@@@@@@@@@@@@@@@@");
			val summaries = Summarizer(filteredTweets)
			.map(tweet=> tweet._1)

			summaries.foreach(println);
			context.actorFor("../Renderer") ! Summary(summaries);

		}
	}

	def summarizationFilter(tweetList:IndexedSeq[String]): IndexedSeq[String] ={
		val taggedTokens = tweetList
		.map(tweet => Tagger(tweet))

		val filteredTweets = tweetList.filter(tweet => !English.isImperative(tweet) && English.isSafe(tweet) && !English.isPersonal(tweet))
		filteredTweets
	}
}

class SAnalyzer(players:List[String]) extends Actor with ActorLogging
{
	
	def receive = {
		case Payload(tweetList:IndexedSeq[String]) => {
			
			//println(tweetList)
			val playersScore:Map[String,Double]=SAnalyzer(players,tweetList.toIndexedSeq);
			context.actorFor("../Renderer") ! PlayerScores(playersScore);
			//println(playersScore)
		}
	}
}




class Labeller extends Actor with ActorLogging {
	import GameAnalyst._
	import scala.concurrent.duration._
	import scala.concurrent.ExecutionContext
	import MessageStore._

	implicit val ec = ExecutionContext.Implicits.global

	context.system.scheduler.schedule(1000.millis,60000.millis,context.self, Report)
	var minute=0;
	var counter=0;
	val threshold = 500;
	var current=0;
	var old =0;
	var switch=0;

	def receive = {
		
		case fullStatus:FullStatus => {
			counter = counter +1;

		}

		case Report => 
		{
		 minute += 1;
		 old = current;
		 current = counter;

		 println("count is" +counter);

		 if(current - old > threshold)  
		 {
		 	switch = switch+1
		 	context.actorFor("../Selector") ! Label("Burst"+"-"+minute,current,minute)
		 }

		 else
		 	context.actorFor("../Selector") ! Label("Normal"+"-"+minute,current,minute)

		 counter=0;
		}
	}
}


abstract class TweetWriter(fileName:String) {
	import java.io.FileWriter
	val wr = new FileWriter(fileName)
	def write(entry:String)

	def closeWriter() = wr.close;

}

class Locator extends TweetWriter("location.txt") with Actor with ActorLogging  
{
	import MessageStore._
	import footballTwitter.util.Tweet._;

	val placeMap = scala.collection.mutable.Map[String,Int]().withDefaultValue(0);
	def receive = {
		case fullStatus: FullStatus => {
			val place = fullStatus.status.getPlace;
			val user = fullStatus.status.getUser


			val country = if(place != null)
							place.getCountry.toLowerCase.trim

			else if(user != null && user.getLocation.trim.length > 0)
							user.getLocation.toLowerCase.trim
			else 
					Tweet.getLanguage(fullStatus);

			write(fullStatus.status.getId + "~~~~~~~~" + country);
		}
	}

	def write(entry:String) 
	{
		wr.write(entry + "\n");
		wr.flush
	}

	override def postStop =closeWriter
	
}


/*
class to handle command line arguements - 
Uses Scallop
*/

class Conf(arguements:Seq[String]) extends ScallopConf(arguements) 
{
	banner("""
			Twitter Soccer Streaming
	For usagse see below :

	-t --terms <arg>	The terms associated with the game that will be streamed
						For example if you want to stream a game between FcBarcelona and AC Milan
						then possible terms can be "fcb" "acmilan" "acm" "barcelona"

	-p --players <arg>  The players whose ratings have to  be monitored in real time
 
		""")

	version(""" Version 0.2 2013 """);
	val terms = opt[List[String]]("terms",descr="The terms that you need to stream");
	val players = opt[List[String]]("players",descr="The List of players who u want to follow");

} 

