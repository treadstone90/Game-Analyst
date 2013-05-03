package footballTwitter.twitter

import scala.collection.JavaConversions._;
import scala.io.Source
import footballTwitter.util.Tagger
import footballTwitter.util.TaggedToken
import chalk.lang.eng.Twokenize;
import footballTwitter.util.English;
import footballTwitter.util.TweetInfo;

object Visualize
{
	def main(args:Array[String])
	{
		val volume = io.Source.fromFile(args(0)).getLines
		.toIndexedSeq
		.map{line=>
			val tokens = line.split("~~~~~~~~");
			val min = tokens(0).split(":")(0)	
			(min,tokens(0).split(":")(1))
		}
		.groupBy(x=> x._1)
		.toIndexedSeq
		.map(x=> (x._2(0))._2)
		

		volume.foreach(println)

	}
}