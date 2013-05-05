package footballTwitter.util
import scala.collection.JavaConversions._;
import org.apache.commons.math3.linear.ArrayRealVector
import scala.io.Source
import chalk.lang.eng.Twokenize



object Summarizer
{

	def apply(tweets:IndexedSeq[String]) ={
		val tokenizedTweets = tweets.map(Twokenize(_))

		val vocabulary = tokenizedTweets.flatten
		.toSet
		.toIndexedSeq

		// tweetMaps will have the term Frequency vector of the twees
		val tweetMaps = tokenizedTweets
			.map(tweet => tweet.groupBy(x=>x)
			.mapValues(x=> x.length)
			.withDefaultValue(0))

		val IDFMap = IDF(vocabulary,tweetMaps)

		val featureVectors = tweetMaps
		.map(tweetMap => Vector(vocabulary,tweetMap,IDFMap))

		val rankedTweets = rank(tweets,featureVectors);
		rankedTweets.reverse;

	}

	def rank(tweets:IndexedSeq[String],tweetVectors: IndexedSeq[ArrayRealVector]) = 
	{
		val scoredTweets = scala.collection.mutable.ArrayBuffer[(String,Double)]()
		val similarityScore = tweetVectors.map {tweet =>
			CosineSimilarity.score(tweet,tweetVectors)
		}

		var index=0;
		tweets.foreach{tweet =>
			val score = similarityScore(index) + POSScore(tweet);
			index+=1;
			scoredTweets += Tuple2(tweet,score)
		}
		
		val rankedTweets = scoredTweets.sortBy(x=> x._2).takeRight(5);
		rankedTweets
	}

	def POSScore(tweet:String) ={
		val tagged = Tagger(tweet)
		
		val count=tagged.count(taggedToken=>  taggedToken.tag == "V"  || taggedToken.tag == "^"   
			|| taggedToken.tag == "Z" )

		count*1.0/Twokenize(tweet).length

	}
	def IDF(vocabulary:IndexedSeq[String], documents: IndexedSeq[Map[String,Int]]) : Map[String,Double]=
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

object SAnalyzer
{
	def apply(players:List[String],tweetList:IndexedSeq[String]) =
	{
		val playerScores = scala.collection.mutable.Map[String,Double]().withDefaultValue(0.5);
		players.foreach { player =>
			val playerTweets:Seq[String] = tweetList.filter(_.contains(player));
			val tokenizedTweets = playerTweets.map(tweet => Twokenize(tweet))
			val tweetsSentiment = tokenizedTweets.map{ tweet =>
				val positive = tweet.count(English.posWords)
				val negative = tweet.count(English.negWords)

				if(positive == negative) 0.5
				else if(positive > negative) 1
				else 0
			}

			if(tweetsSentiment.length > 0)
				playerScores(player) = tweetsSentiment.sum/tweetsSentiment.length;
		}

		playerScores.toMap
	}
}

object CosineSimilarity
{

	def score(vector:ArrayRealVector, tweetVectors:IndexedSeq[ArrayRealVector]) =
	tweetVectors.map(x=> cosineSimilarity(vector,x)).sum



	def cosineSimilarity(vector1:ArrayRealVector,vector2:ArrayRealVector)=
	vector1.dotProduct(vector2)/(vector1.getNorm*vector2.getNorm)
}

/*def apply(vectors: IndexedSeq[ArrayRealvector]) = 
	{
		
		for(i <- (0 to vectors.length-1)) {
			var first =i;

			for(j <- (0 to vectors.length-1)) {
				var second =j;

				if(!cosineSimilarities.contains(Set(i,j)))
					cosineSimilarities(Set(i,j)) = cosineSimilarity(tweetVectors(i),tweetVectors(j));
			}
		}		
	}*/

object Vector {
	def apply(vocabulary:IndexedSeq[String],document: Map[String,Int],IDFMap:Map[String,Double]) ={
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