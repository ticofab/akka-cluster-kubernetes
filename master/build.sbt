name := "master"
version := "0.0.1"
scalaVersion := "2.12.6"
organization := "ticofab.io"

lazy val common = RootProject(file("../common"))
val main = Project(id = "master", base = file(".")).dependsOn(common)

libraryDependencies ++= {
  val circeVersion = "0.9.3"

  Seq(
    // akka http for server tuff
    "com.typesafe.akka" %% "akka-http" % "10.1.4",

    // json
    "io.circe" %% "circe-core" % circeVersion,
    "io.circe" %% "circe-generic" % circeVersion,

    // kubernetes stuff
    "io.fabric8" % "kubernetes-client" % "3.1.1",
    "io.fabric8" % "kubernetes-api" % "3.0.8"
  )
}

enablePlugins(JavaAppPackaging)
enablePlugins(AshScriptPlugin)

mainClass in Compile := Some("io.ticofab.akkaclusterkubernetes.AkkaClusterKubernetesMasterApp")
packageName in Docker := "adam-akka/" + name.value
version in Docker := "latest"
dockerLabels := Map("maintainer" -> organization.value, "version" -> version.value)
dockerBaseImage := "openjdk:8-jre"
defaultLinuxInstallLocation in Docker := s"/opt/${name.value}" // to have consistent directory for files
dockerRepository := Some("eu.gcr.io")
