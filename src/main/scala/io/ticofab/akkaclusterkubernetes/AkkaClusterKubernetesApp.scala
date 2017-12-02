package io.ticofab.akkaclusterkubernetes

import akka.actor.{ActorSystem, Props}
import com.typesafe.config.ConfigFactory
import io.ticofab.akkaclusterkubernetes.actor.{InputSource, Master}

object AkkaClusterKubernetesApp extends App {
  implicit val as = ActorSystem("akka-cluster-kubernetes")
  implicit val ec = as.dispatcher

  val roles = ConfigFactory.load().getStringList("akka.cluster.roles")

  if (roles.contains("seed")) {
    // create mast and input source actors
    val master = as.actorOf(Props[Master], "master")
    as.actorOf(Props(new InputSource(master)), "inputSource")
  }

  // https://hackernoon.com/akka-streams-a-story-of-scalability-5d9e7c2d3ac3
  // https://doc.akka.io/docs/akka/2.5.7/distributed-pub-sub.html?language=scala
  // http://letitcrash.com/post/29044669086/balancing-workload-across-nodes-with-akka-2

}
