name := "worker"
version := "0.0.1"
scalaVersion := "2.12.6"
organization := "ticofab.io"

lazy val common = RootProject(file("../common"))
val main = Project(id = "worker", base = file(".")).dependsOn(common)
