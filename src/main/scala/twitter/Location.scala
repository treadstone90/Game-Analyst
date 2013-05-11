package footballTwitter.twitter

import scala.collection.JavaConversions._

import akka.actor._
import twitter4j._

  case class LocationConfidence(latitude: Double, longitude: Double, confidence: Double)





/**
 * An actor that wraps the GeoNames API so only one thing accesses it at a
 * time.
 */
class GeoNames(username: String) {
  import org.geonames
  

  geonames.WebService.setUserName(username)

  def locatePlaceByName(placeName: String): Option[LocationConfidence] = {
  //  log.info("Searching GeoNames for '" + placeName + "'")

    // Get a collection of populated places matching the name
    val searchCriteria = new geonames.ToponymSearchCriteria()
    searchCriteria.setQ(placeName)
    searchCriteria.setFeatureClass(geonames.FeatureClass.P)
    searchCriteria.setStyle(geonames.Style.LONG)
    val searchResult = geonames.WebService.search(searchCriteria)
    val toponyms = searchResult.getToponyms
    val populatedToponyms = toponyms.filter(t => Option(t.getPopulation).isDefined)

    // Take the top result
    if (populatedToponyms.length > 0) {
      val toponym = populatedToponyms(0)
      Some(new LocationConfidence(toponym.getLatitude, toponym.getLongitude, 0.7))
    } else {
      None
    }
  }
}
