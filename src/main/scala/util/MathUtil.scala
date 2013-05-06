package footballTwitter.util
import scala.collection.JavaConversions._;
import org.apache.commons.math3.linear.ArrayRealVector

/** Computes the Jaccard Similarity betweeb two Strings*/
object JaccardSimilarity {
  def apply(str1: String, str2: String): Double = {
    val shingled1 = str1.toIndexedSeq.sliding(4).toSet;
    val shingled2 = str2.toIndexedSeq.sliding(4).toSet;

    val intersect = shingled1.intersect(shingled2);
    val union = shingled1.union(shingled2);

    1.0 * intersect.size / union.size;
  }
}

/** Computes the Cosine Similarity between two Vectors */
object CosineSimilarity {

  def score(vector: ArrayRealVector, tweetVectors: IndexedSeq[ArrayRealVector]) =
    tweetVectors.map(x => cosineSimilarity(vector, x)).sum

  def cosineSimilarity(vector1: ArrayRealVector, vector2: ArrayRealVector) =
    vector1.dotProduct(vector2) / (vector1.getNorm * vector2.getNorm)
}

/** Vectorizes a document to the TF-IDF Vector*/

object Vector {
  def apply(vocabulary: IndexedSeq[String], document: Map[String, Int], IDFMap: Map[String, Double]) = {
    val vector = new ArrayRealVector(vocabulary.size)

    var index = 0;
    vocabulary.foreach { word =>
      val wc = document(word)
      val idf = IDFMap(word);
      vector.setEntry(index, wc * idf);
      index += 1;
    }
    vector

  }
}