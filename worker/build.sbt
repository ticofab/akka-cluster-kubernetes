
name := "worker"
version := "0.0.1"
scalaVersion := "2.12.6"
organization := "ticofab.io"

lazy val common = RootProject(file("../common"))
val main = Project(id = "worker", base = file(".")).dependsOn(common)

enablePlugins(JavaAppPackaging)
enablePlugins(AshScriptPlugin)

mainClass in Compile := Some("io.ticofab.akkaclusterkubernetes.AkkaClusterKubernetesWorkerApp")
packageName in Docker := "adam-akka/" + name.value
version in Docker := "latest"
dockerLabels := Map("maintainer" -> organization.value, "version" -> version.value)
dockerBaseImage := "openjdk:8-jre"
defaultLinuxInstallLocation in Docker := s"/opt/${name.value}" // to have consistent directory for files
dockerRepository := Some("eu.gcr.io")
