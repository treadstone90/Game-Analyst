package footballTwitter.util
import chalk.lang.eng.Twokenize
/*
Guesses the language of the tweets based on some criteria
*/


/* provides functionality related to processing tweets */

object Tweet {
	import footballTwitter.twitter.MessageStore.FullStatus
	def normalize(tweet:String) = Twokenize(tweet).mkString(" ").toLowerCase;

	def getLanguage(fullStatus:FullStatus)={
		val json = fullStatus.JSON
		try
		{
			val langRegex = """e,"lang":"([a-z]{1,6})","entities""".r
			val lang = langRegex.findAllIn(json).matchData.toList(0).group(1)
			lang
		}
		catch{
		case ioe: java.lang.IndexOutOfBoundsException => println("ooops");println(json);"na"
		}	
	}
}

case class TweetInfo(
	minute:Int,
	tag:String,
	tweet:String,
	FilteredTweet:String
	)


object JaccardSimilarity
{
	def apply(str1:String,str2:String):Double ={
		//println(str1 + " ~~~~~" + str2);
		val shingled1 = str1.toIndexedSeq.sliding(4).toSet;
		val shingled2 = str2.toIndexedSeq.sliding(4).toSet;

		val intersect = shingled1.intersect(shingled2);
		val union = shingled1.union(shingled2);

		1.0*intersect.size/union.size;
	}
}