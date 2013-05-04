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
	case class Payload(tweetList:ArrayBuffer[String])
}


// The Actor System

object GameAnalyst
{

	import MessageStore._

	def main(args:Array[String]) {
	val system = ActorSystem("Analyst");
		val conf  = new Conf(args);
		val terms = conf.terms().toArray
		println(terms);
		val ids = conf.ids()

		
		val manager = system.actorOf(Props[Manager],name ="Manager");
		manager ! Stream(terms);
		
	}
}

class Manager extends Actor with ActorLogging {	
	import GameAnalyst._;	
	import MessageStore._

	val streamManager = context.actorOf(Props[StreamManager],name ="StreamManager");
	val summarizer = context.actorOf(Props[Summarizer],name ="Summarizer");
	//val sentiAnalyzer = context.actorOf(Props[SentimentAnalyzer],name ="SentimentAnalyzer");

	def receive ={
		case Stream(terms) =>  println("hi"); streamManager ! Stream(terms)

	}
}

class StreamManager extends Actor with ActorLogging with TermFilter
{
	import GameAnalyst._;
	import MessageStore._

	val streamer = new Streamer(context.self);
	val locator = context.actorOf(Props[Locator], name ="Locator")
	val counter = context.actorOf(Props[Counter], name ="Counter")
	val selector = context.actorOf(Props[SummarizationCandidates],name ="SummarizationCandidates")
	val labeller = context.actorOf(Props[Labeller],name ="Labeller")
	var tweetCount=0;
	
	override def preStart ={
		//streamer.stream.sample
	}

	def receive  = {
		case Stream(terms) => streamer.stream.filter(getQuery(terms))
		case Shutdown => {
			labeller ! Shutdown
			streamer.stream.shutdown;
		}
		
		case fullStatus : FullStatus => {

		//println(fullStatus.status.getText)
		tweetCount = tweetCount +1
		
		counter ! fullStatus
		labeller ! fullStatus
		//selector ! fullStatus
		locator ! fullStatus		
		
		}
	}



}

class Labeller extends Actor with ActorLogging
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
			println("rvd here")
			if(Tweet.getLanguage(fullstatus) == "en" && !fullstatus.status.isRetweet && fullstatus.status.getMediaEntities.length == 0)
			{
				val normalizedTweet = English.removeNonLanguage(Tweet.normalize(fullstatus.status.getText));

				val entry:String = gameMinutes+":"+count+":"+tag + "~~~~~~~~"+ normalizedTweet;

				
				if(Math.random <=0.7 && Twokenize(normalizedTweet).length > 3)
				{
					println("pushed to aggregator")
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
}

class Aggregator extends Actor with ActorLogging
{
	val tweetsPot = scala.collection.mutable.ArrayBuffer[String]()
	var prevTag = "";
	def receive ={
		case TaggedTweet(tag:String,tweet:String) => {
			println("revd from labeller")
			if(tag == prevTag)
				tweetsPot.append(tweet);
			else
			{
				println("sending for processing")
				context.actorFor("../../../Summarizer")! Payload(tweetsPot.map(x=>x))
				//SentimentAnalyzer ! Payload(tweetsPot.map(_))
			}
			prevTag = tag
		}
	}
}

class Summarizer extends Actor with ActorLogging
{
	def receive ={
		case Payload(tweetList:ArrayBuffer[String]) => {
			println("here received payload");
			println(tweetList.length)
		}
	}
}

class SAnalyzer extends Actor with ActorLogging
{
	// issue one is how do I specify the player names.. I guess I would neeed to do do oti for all players
	def receive = {
		case Payload => {
			//now extract the list and perform sentiment analyzsis
		}
	}
}


class SummarizationCandidates extends Actor with ActorLogging
{
	import GameAnalyst._;
	import MessageStore._;
	import footballTwitter.util.English;
	import footballTwitter.util.SimpleTokenizer;
	import java.io.PrintWriter
	import footballTwitter.util.Tweet._;

	val candidates = scala.collection.mutable.HashSet[String]()

	def receive ={
		case fullstatus:FullStatus =>{
			val text = fullstatus.status.getText

			admissable(fullstatus) match {
				case Some(text) => println(text);candidates += text
				case None => //println("");
			}

		}

		if(candidates.size > 500)
		{
			val wr = new PrintWriter("candidates.txt");

			candidates.foreach { line=>
				wr.write(line  +"\n");
			}
			wr.close
			context.stop(self)

		}
		println(candidates.size)
	}

	def admissable(fullstatus:FullStatus):Option[String] ={
		val tweet = fullstatus.status.getText

		val content = if(English.isEnglish(tweet) && !tweet.contains("quote") 
			&& Tweet.getLanguage(fullstatus).equals("en")) 
		{
			SimpleTokenizer(English.removeNonLanguage(tweet))
			.filterNot(_.contains('/'))
			.mkString(" ")
		}
		else ""

		if(SimpleTokenizer(content).length > 4)
		Some(content)
		else
		None
	}
}


class Counter extends Actor with ActorLogging {
	import GameAnalyst._
	import scala.concurrent.duration._
	import scala.concurrent.ExecutionContext
	import MessageStore._

	implicit val ec = ExecutionContext.Implicits.global

	context.system.scheduler.schedule(1000.millis,20000.millis,context.self, Report)
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
		 	context.actorFor("../Labeller") ! Label("Burst"+"-"+minute,current,minute)
		 }

		 else
		 	context.actorFor("../Labeller") ! Label("Normal"+"-"+minute,current,minute)

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
	
	val terms = opt[List[String]]("terms",descr="The terms that you need to stream");
	val ids = opt[List[String]]("ids",descr = "the user ids which you want to stream");
} 

