name := "akka-cluster-kubernetes"

version := "0.0.1"

scalaVersion := "2.12.4"

libraryDependencies ++= {
  val akkaVersion = "2.5.7"
  Seq(
    "com.typesafe.akka" %% "akka-actor" % akkaVersion,
    "com.typesafe.akka" %% "akka-stream" % akkaVersion,
    "com.typesafe.akka" %% "akka-cluster" % akkaVersion
  )
}
