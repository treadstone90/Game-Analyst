package footballTwitter.util
import chalk.lang.eng.Twokenize


/* provides functionality related to processing tweets */

object Tweet {
  import footballTwitter.twitter.MessageStore.FullStatus
  def normalize(tweet: String) = Twokenize(tweet).mkString(" ").toLowerCase;

  def getLanguage(fullStatus: FullStatus) = {
    val json = fullStatus.JSON
    try {
      val langRegex = """e,"lang":"([a-z]{1,6})","entities""".r
      val lang = langRegex.findAllIn(json).matchData.toList(0).group(1)
      lang
    } catch {
      case ioe: java.lang.IndexOutOfBoundsException => println("ooops"); println(json); "na"
    }
  }
}


/* A case class to associat tweets with timestamp , tag and the raw Tweet */

case class TweetInfo(
  minute: Int,
  tag: String,
  tweet: String,
  FilteredTweet: String)
