name := "akka-cluster-kubernetes"
version := "0.0.1"
scalaVersion := "2.12.5"
organization := "ticofab.io"

libraryDependencies ++= {
  val akkaVersion = "2.5.11"
  Seq(

    // akka stuff
    "com.typesafe.akka" %% "akka-actor" % akkaVersion,
    "com.typesafe.akka" %% "akka-stream" % akkaVersion,
    "com.typesafe.akka" %% "akka-cluster" % akkaVersion,
    "com.typesafe.akka" %% "akka-http" % "10.1.0",

    // kubernetes stuff
    "io.fabric8" % "kubernetes-client" % "3.1.1",
    "io.fabric8" % "kubernetes-api" % "3.0.8",

    // logging
    "ch.qos.logback" % "logback-classic" % "1.2.3",
    "com.typesafe.akka" %% "akka-slf4j" % akkaVersion,
    "com.typesafe.scala-logging" %% "scala-logging" % "3.8.0",

    // ficus for config
    // https://github.com/iheartradio/ficus
    "com.iheart" %% "ficus" % "1.4.3"
  )
}

enablePlugins(JavaAppPackaging)
enablePlugins(DockerPlugin)
enablePlugins(AshScriptPlugin)

mainClass in Compile := Some("io.ticofab.akkaclusterkubernetes.AkkaClusterKubernetesApp")

lazy val shortCommit = ("git rev-parse --short HEAD" !!).replaceAll("\\n", "").replaceAll("\\r", "")
lazy val branch = ("git rev-parse --abbrev-ref HEAD" !!).replaceAll("\\n", "").replaceAll("\\r", "")

packageName in Docker := "adam-akka/" + name.value
version in Docker := "latest"
dockerLabels := Map("maintainer" -> organization.value, "branch" -> branch, "version" -> version.value)
dockerBaseImage := "openjdk:8-jre"
defaultLinuxInstallLocation in Docker := s"/opt/${name.value}" // to have consistent directory for files
dockerRepository := Some("eu.gcr.io")

