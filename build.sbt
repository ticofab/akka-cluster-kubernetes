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
version in Docker := "latest"
dockerLabels := Map("maintainer" -> organization.value, "branch" -> branch, "version" -> version.value)
dockerBaseImage := "openjdk:8-jre"
defaultLinuxInstallLocation in Docker := s"/opt/${name.value}" // to have consistent directory for files
dockerRepository := Some("eu.gcr.io")

//universal:packageBin

// deploy:
// docker:publishLocal
// gcloud docker -- push eu.gcr.io/adam-akka/akka-cluster-kubernetes:latest

// kubectl get pods (I'll see what is running)

// kubectl apply -f master.yaml (this starts the thing)

// watch kubectl get pods (this devotes one screen to monitoring the pods)

// kubectl logs -f akka-master-0 (see the logs from a pod, also available in stack driver)

// kubectl describe statefulset akka-master
// kubectl get statefulset akka-worker (this is good to watch)
// kubectl get statefulset akka-worker -o yaml (this outputs the information in yaml format and more closely reflects the API)
// kubectl get statefulset akka-worker -o json | jq .status (this is tool to parse json stuff)

// kubectl get services (see ip addresses)

// kubectl delete pod akka-master-0

// stop working:
// kubectl delete -f master.yaml

// redeploy:
// kubectl delete statefulset akka-master
// kubectl apply -f master.yaml