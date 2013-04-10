package footballTwitter.util
import twitter4j._
import twitter4j.conf.ConfigurationBuilder;
import akka.actor._
import footballTwitter.twitter._;
import twitter4j.json.DataObjectFactory


// The purpose of this file is to take a twitter4j stream and 
//give it to AKKA. Similar to what Jason Baldridge did in his 
// code. 

trait StreamInstance {
	val cb = new ConfigurationBuilder();
	cb.setJSONStoreEnabled(true);
	val con = cb.build
	
	val stream = new TwitterStreamFactory(con).getInstance
}

class Streamer(actor:ActorRef) extends StreamInstance{
	import MessageStore._

	class Listener extends StatusListenerAdaptor{
		override def onStatus(status: Status) = {
			val JSON=DataObjectFactory.getRawJSON(status)
			actor ! FullStatus(status,JSON)
	}
}

	stream.addListener(new Listener);
}



