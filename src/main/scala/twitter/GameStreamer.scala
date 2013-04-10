package footballTwitter.twitter

import twitter4j._
import tokenize.Twokenize;
import scala.collection.JavaConversions._;
import akka.util._
import akka.actor._;
import footballTwitter.util._
import footballTwitter.twitter._
import org.rogach.scallop._
import sys.process._
import scala.io.Source




object MessageStore{
	object Locate // this is from the manager to the person doing the location
	object Shutdown // this is from the manager
	object Start // this is for the manager
	object Report // from counter to counter
	case class Rate(x:Int) // from counter to manager
	case class Stream(terms:Array[String])
	case class FullStatus(status:Status,JSON:String)
}


// The Actor System

object GameAnalyst
{

	import MessageStore._

	def main(args:Array[String]) {
	val system = ActorSystem("Analyst");
		val conf  = new Conf(args);
		val terms = conf.terms().toArray
		val ids = conf.ids().toArray

		
		val manager = system.actorOf(Props[Manager],name ="Manager");
		manager ! Stream(terms);
		
	}
}

class Manager extends Actor with ActorLogging {	
	import GameAnalyst._;	
	import MessageStore._

	val streamManager = context.actorOf(Props[StreamManager],name ="StreamManager");

	def receive ={
		case Stream(terms) => streamManager ! Stream(terms)
		case Rate(count) => println(); // non functional in this branch as used only for "topic" tweets

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
	var tweetCount=0;
	
	override def preStart ={
		streamer.stream.sample
	}

	def receive  = {
		case Stream(terms) => streamer.stream.filter(getQuery(terms))
		case Shutdown => streamer.stream.shutdown;
		
		case fullStatus : FullStatus => {
		tweetCount = tweetCount +1
		selector ! fullStatus
		locator ! fullStatus		
		counter ! fullStatus
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
	import Language._

	val candidates = scala.collection.mutable.HashSet[String]()

	def receive ={
		case fullstatus:FullStatus =>{
			val text = fullstatus.status.getText
			admissable(fullstatus) match {
				case Some(text) => println(text);candidates += text
				case None => //println("");
			}

		}

		if(candidates.size > 1000)
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
			&& Language.getLanguage(fullstatus).equals("en")) 
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

	context.system.scheduler.schedule(1000.millis,1200000.millis,context.self, Report)
	var counter=0;

	def receive = {
		
		case fullStatus:FullStatus => {
			counter = counter +1;
		}

		case Report => {
		 context.actorFor("../../") ! Rate(counter)
		 counter=0;
		}
	}
}



class Locator extends Actor with ActorLogging
{
	import MessageStore._
	import Language._

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
					Language.getLanguage(fullStatus);

			placeMap(country) += 1
			println(country)
			println(placeMap.size)
		}
	}
	
}


/*
class to handle command line arguements - 
Uses Scallop
*/

class Conf(arguements:Seq[String]) extends ScallopConf(arguements) {
	val terms = opt[List[String]](required = true)
	val ids = opt[List[String]]()

} 

/*
Guesses the language of the tweets based on some criteria
1. Uses the new twiter APi which has lang field
2. Some times they returned NULL => so use the languge of the user 
hoping they mihgt be very similar
3. In the futre hope to use Bing API
*/
object Language {
	import MessageStore._
	def getLanguage(fullStatus:FullStatus)={
			val tweetMap = scala.util.parsing.json.JSON.parseFull(fullStatus.JSON)
			.get
			.asInstanceOf[Map[String,String]]
			
			val language = tweetMap.get("lang");
			val ret=language match {
				case Some(x) => x
				case None => if(fullStatus.status.getUser != null) fullStatus.status.getUser.getLang else "na"
			}
		ret
	}
}