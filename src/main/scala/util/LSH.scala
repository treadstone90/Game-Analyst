/*package footballTwitter.util
import scala.util.Random

class LSH(
	tweets:IndexedSeq[String],
	numBuckets: Int
	)
{
	val permutationList = scala.collection.mutable.ArrayBuffer[IndexedSeq[Int]]();
	val bandMaps = scala.collection.mutable.IndexedSeq[BucketHash]();
	


	def apply() {
		val shingleMap:Map[String,Set[String]] = tweets.map{tweet=> 
			val shingles = tweet.toList.sliding(4)
			.map(_.mkString).toSet
			(tweet,shingles)
		}.toMap


		val shingleVocab = shingleMap.values.flatten.toSet.toIndexedSeq
		println(shingleVocab);
		println(shingleVocab.length)
		println("f")

		(1 to 20).toList.foreach(i=> 
	permutationList.append(Random.shuffle( (0 to shingleVocab.length-1).toIndexedSeq)))

		val tweetMinHash= shingleMap.mapValues{ value => 
			val fullVector = shingleVocab.map(shingle => if(value(shingle)) 1 else 0)
			//println(fullVector)
			val minHash =getMinHash(fullVector);
			//println(minHash)
			minHash
		}
			
	val bands= 5;

	val buckets= (0 to bands).stoList.flatMap{ partition =>
		val start = partition*(permutationList.length)/bands;
		val end = partition + (permutationList.length)/bands;
		val bucketList = new BucketHash(numBuckets);
		val bandSignatureMap=tweetMinHash.mapValues(value => value.slice(start,end))
		.foreach{ case(key,value) => bucketList.put(value,key.toInt) }

		bucketList.table
	}


	// now we need to make clusters

	(0 to tweets.length-1).toList.foreach{ index=>
		val containedBuckets=buckets.filter(bucket=> bucket(index));
		val candidates = containedBucket.flatten.toSet 
			- index.toSet - (index+1 to tweets.length-1).toSet

		if(candidates.length == 0)
			// ondi puli

		else 



	}

	





	}


	def getMinHash(shingleVector:IndexedSeq[Int]) ={
		println("inside function call")
		val minHash=permutationList.flatMap{ permutation => 
			val paired = permutation.zip(shingleVector);
			println(paired);
			paired.sortBy(x=> x._1).find(pair=> pair._2 == 1)
		}.map(x=> x._1)
		minHash.toIndexedSeq
	}

	
}

object Test
{
	def main(args:Array[String]){
		val tweets = io.Source.fromFile("sample.txt").getLines.toIndexedSeq

		val obj = new LSH(tweets,10);
		obj.apply()
	}
}


class BucketHash(size:Int) {

	val table = Array.fill[Bucket](size)(new Bucket)

	def put(minHash:IndexedSeq[Int],tweetIndex:Int){
		val hash = minHash.hashCode;
		val index = hash % table.length-1;
		table(index).add(tweetIndex);
	}

	def getBucket(minHash:IndexedSeq[Int]):Bucket ={
		val hash = minHash.hashCode
		val index = hash % table.length
		table(index);
	}




}

class Bucket{
	val entries = scala.collection.mutable.Set[Int](); // where each entry is a...

	def add(tweet:Int) =  entries.add(tweet);
}*/