import scala.collection.JavaConversions._
import twitter4j.TwitterFactory
import java.io._

object testUser {
	def main(args:Array[String])
	{
		val userId =args(0).toLong
		val fileName = args(1)
		val twitter = new TwitterFactory().getInstance
		var cursor = -1.toLong
//		println(twitter.getRateLimitStatus)

		val wr = new FileWriter(new File(fileName))

		var page=0;
		do{
			val IDs =twitter.getFollowersIDs(userId,cursor)
			val followerIDS = IDs.getIDs
			cursor = IDs.getNextCursor
			page += 1	
			arrayToFile(wr,followerIDS);
			println(twitter.getRateLimitStatus)

		}while(page < 2)
		wr.close

	}

	def arrayToFile(wr : FileWriter,ids: Array[Long]) {
		ids.foreach(id=>wr.write(id.toString + "\n"))
	}
}/*
object getUserLocation{
	def main(argsL Array[String])
	{
		val fileName = 
	}

}*/