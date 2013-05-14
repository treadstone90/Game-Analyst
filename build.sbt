name := "footballTwitter"

version := "0.1.5-SNAPSHOT"

organization := "edu.utexas"

scalaVersion := "2.10.1"

crossPaths := false

retrieveManaged := true

resolvers += "Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/"

resolvers += "spray repo" at "http://repo.spray.io"

resolvers += "SonaType Repo" at "https://oss.sonatype.org/content/repositories/snapshots"

libraryDependencies ++= Seq(
  "org.twitter4j" % "twitter4j-core" % "3.0.3",
  "org.twitter4j" % "twitter4j-stream" % "3.0.3",
  "org.scalanlp" % "nak" % "1.1.2",
  "org.scalanlp" % "chalk" % "1.1.3-SNAPSHOT",
  "com.typesafe.akka" %% "akka-actor" % "2.1.2",
  "commons-codec" % "commons-codec" % "1.7",
  "org.apache.lucene" % "lucene-core" % "4.2.0",
  "org.apache.lucene" % "lucene-analyzers-common" % "4.2.0",
  "org.apache.lucene" % "lucene-queryparser" % "4.2.0",
  "org.rogach" %% "scallop" % "0.8.1",
  "org.parboiled" % "parboiled-scala" % "1.0.0",
  "io.spray" %%  "spray-json" % "1.2.3",
  "org.apache.commons" % "commons-math3" % "3.0",
  "jdom" % "jdom" % "1.0"
)
