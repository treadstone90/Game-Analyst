package footballTwitter.twitter
import scala.collection.JavaConversions._;
import footballTwitter.util.SimpleTokenizer
import org.apache.commons.math3.linear.ArrayRealVector
import scala.io.Source
import footballTwitter.util.Tagger
import footballTwitter.util.TaggedToken
import chalk.lang.eng.Twokenize
import footballTwitter.util.TweetInfo;

// The offline summarizer
// args - reads the file produced by the streamer which has a filtered list of 
// output => potential candidates for summarization

object Main{
	def main(args:Array[String])
	{
		val tweetsInfo = io.Source.fromFile(args(0)).getLines
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
			
		val orderedEvents = tweetGroups
		.keys
		.toIndexedSeq
		.sortBy(x=> (x.split("-")(1).toInt,x.split("-")(0)))
		

		val vocabulary = tweetsInfo
		.flatMap(tweetInfo=> Twokenize(tweetInfo.tweet))
		.toSet
		.toIndexedSeq
	
		val tweetMaps = tweetsInfo
		.map(tweetInfo=> Twokenize(tweetInfo.tweet))
		.map(tweet => tweet.groupBy(x=>x)
		.mapValues(x=> x.length)
		.withDefaultValue(0))

		val IDFMap = IDF(vocabulary,tweetMaps);

		val featureVectors = tweetMaps
		.map(x=> Vector.getFeatureVector(vocabulary,x,IDFMap))

		
		
		orderedEvents.foreach {event=>
		val tweetInfo = tweetGroups(event);
		rank(tweetInfo.map(_.tweet),featureVectors).take(1).foreach(x=> println(x._1))
		}

	}

	// Ranks the tweets based on two criterion
	// The cosine similarty beween documents and POS Score

	def rank(tweets:IndexedSeq[String],featureVectors:IndexedSeq[ArrayRealVector]) ={

		var index = -1;
		val rankedTweets = scala.collection.mutable.ArrayBuffer[(String,Double)]()
		
		val cosScore= tweets.map {tweet=> 
			index = index+1;
			cosineScore(featureVectors(index),featureVectors) 
		};


		val sum = cosScore.sum
		val normlizedCosineScore = cosScore.map(x=> x/sum)
		index=0;
		tweets.foreach{x=>
			val score = normlizedCosineScore(index) + POSScore(x)
			index += 1
			rankedTweets += Tuple2(x,score)
		}
		val rerankedTweets = rankedTweets.sortBy(x=> x._2).reverse

		rerankedTweets

	}

	// not efficient- will be changed in 0.2
	def cosineScore(tweetVector:ArrayRealVector, featureVectors:IndexedSeq[ArrayRealVector])=
		featureVectors.map(x=> cosineSimilarity(tweetVector,x)).sum
	

	def cosineSimilarity(vector1:ArrayRealVector,vector2:ArrayRealVector)=
	vector1.dotProduct(vector2)/(vector1.getNorm*vector2.getNorm)


	def POSScore(tweet:String) ={
		val tagged = Tagger(tweet)
		
		val count=tagged.count(taggedToken=>  taggedToken.tag == "V"  || taggedToken.tag == "^"   
			|| taggedToken.tag == "Z" )

		count*1.0/Twokenize(tweet).length

	}
	
}



// compute the IDF 

object IDF {
	def apply(vocabulary:IndexedSeq[String], documents: IndexedSeq[Map[String,Int]]) : Map[String,Double]=
	{
		val IDFMap= scala.collection.mutable.Map[String,Double]()
		.withDefaultValue(0);
		
		vocabulary.foreach {word =>
			val df=documents.count(document => document.contains(word))
			IDFMap(word) = Math.log(documents.size/df);
		}

		IDFMap.toMap

	}
}

// get the TF IDF feature vector
object Vector {
	def getFeatureVector(vocabulary:IndexedSeq[String],document: Map[String,Int],IDFMap:Map[String,Double]) ={
		val vector = new ArrayRealVector(vocabulary.size)

		var index=0;
		vocabulary.foreach {word =>
			val wc = document(word)
			val idf = IDFMap(word);
			vector.setEntry(index,wc*idf);
			index +=1;
		}
		vector


	}
}



