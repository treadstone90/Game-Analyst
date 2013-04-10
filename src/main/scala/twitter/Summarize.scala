package footballTwitter.twitter
import scala.collection.JavaConversions._;
import tokenize.Twokenize;
import footballTwitter.util.SimpleTokenizer
import org.apache.commons.math3.linear.ArrayRealVector
import scala.io.Source
import footballTwitter.util.Tagger
import footballTwitter.util.TaggedToken

// The offline summarizer
// args - reads the file produced by the streamer which has a filtered list of 
// output => potential candidates for summarization


object Main{
	def main(args:Array[String])
	{
		val tweets = Source.fromFile(args(0)).getLines.toIndexedSeq
		val vocabulary = tweets.flatMap(tweet=> SimpleTokenizer(tweet)).toSet.toIndexedSeq
	
		val tweetMaps = tweets.map(tweet=> SimpleTokenizer(tweet))
		.map(tweet => tweet.groupBy(x=>x).mapValues(x=> x.length).withDefaultValue(0))

		val IDFMap = IDF(vocabulary,tweetMaps);

		val featureVectors = tweetMaps
		.map(x=> Vector.getFeatureVector(vocabulary,x,IDFMap))

		rank(tweets,featureVectors).take(40).foreach(x=> println(x._1))
		

	}

	// Ranks the tweets based on two criterion
	// The cosine similarty beween documents and POS Score

	def rank(tweets:IndexedSeq[String],featureVectors:IndexedSeq[ArrayRealVector]) ={

		var index=0;
		val rankedTweets = scala.collection.mutable.ArrayBuffer[(String,Double)]()
		
		val cosScore= tweets.map(tweet=> cosineScore(featureVectors(index),featureVectors));
		val sum = cosScore.sum
		val normlizedCosineScore = cosScore.map(x=> x/sum)

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

		count*1.0/Twokenize.tokenizeRawTweetText(tweet).length

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



