package footballTwitter.twitter

import scala.collection.JavaConversions._

import akka.actor._
import twitter4j._

/**
 * Copyright 2013 Nick Wilson
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

  case class LocationConfidence(latitude: Double, longitude: Double, confidence: Double)





/**
 * An actor that wraps the GeoNames API so only one thing accesses it at a
 * time.
 * @ param username the GeoNames API usernames
 * @constructor create a new instance with the usename 
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
