name := "common"
version := "0.0.1"
scalaVersion := "2.12.6"
organization := "ticofab.io"

libraryDependencies ++= {
  val akkaVersion = "2.5.14"
  Seq(

    // akka stuff
    "com.typesafe.akka" %% "akka-actor" % akkaVersion,
    "com.typesafe.akka" %% "akka-stream" % akkaVersion,
    "com.typesafe.akka" %% "akka-cluster" % akkaVersion,

    // logging
    "org.wvlet.airframe" %% "airframe-log" % "0.51",

    // ficus for config
    // https://github.com/iheartradio/ficus
    "com.iheart" %% "ficus" % "1.4.3"
  )
}
