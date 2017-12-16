name := "akka-cluster-kubernetes"
version := "0.0.1"
scalaVersion := "2.12.4"
organization := "ticofab.io"

libraryDependencies ++= {
  val akkaVersion = "2.5.8"
  Seq(
    "com.typesafe.akka" %% "akka-actor" % akkaVersion,
    "com.typesafe.akka" %% "akka-stream" % akkaVersion,
    "com.typesafe.akka" %% "akka-cluster" % akkaVersion,
    "com.typesafe.akka" %% "akka-http" % "10.0.11",
    "io.fabric8" % "kubernetes-client" % "3.1.1",
    "io.fabric8" % "kubernetes-api" % "3.0.8"
  )
}

enablePlugins(JavaAppPackaging)
enablePlugins(DockerPlugin)
enablePlugins(AshScriptPlugin)

mainClass in Compile := Some("io.ticofab.akkaclusterkubernetes.AkkaClusterKubernetesApp")

lazy val shortCommit = ("git rev-parse --short HEAD" !!).replaceAll("\\n", "").replaceAll("\\r", "")
lazy val branch = ("git rev-parse --abbrev-ref HEAD" !!).replaceAll("\\n", "").replaceAll("\\r", "")

packageName in Docker := "adam-akka/" + name.value
version in Docker := shortCommit
dockerLabels := Map("maintainer" -> organization.value, "branch" -> branch, "version" -> version.value)
dockerBaseImage := "openjdk:8-jre"
defaultLinuxInstallLocation in Docker := s"/opt/${name.value}" // to have consistent directory for files
dockerRepository := Some("eu.gcr.io")

//universal:packageBin
//docker:packageDocker