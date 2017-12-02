package io.ticofab.akkaclusterkubernetes

import akka.actor.{ActorSystem, Props}
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Directives._
import akka.stream.ActorMaterializer
import com.typesafe.config.ConfigFactory
import io.ticofab.akkaclusterkubernetes.actor.Supervisor

object AkkaClusterKubernetesApp extends App {
  implicit val as = ActorSystem("akka-cluster-kubernetes")

  val roles = ConfigFactory.load().getStringList("akka.cluster.roles")
  if (roles.contains("seed")) {
    as.actorOf(Props(new Supervisor), "supervisor")
  }


  /*

    approaches:

    APP level:

      1. akka streams + mapAsync (needs to have the number of worker nodes)
      2. raw actors (distributed pub/sub? mediator? pull work)

            https://hackernoon.com/akka-streams-a-story-of-scalability-5d9e7c2d3ac3
            https://doc.akka.io/docs/akka/2.5.7/distributed-pub-sub.html?language=scala
            http://letitcrash.com/post/29044669086/balancing-workload-across-nodes-with-akka-2


    INFRA level:

      1. using yaml files and deploying and API service to spin up new nodes as needed
      2. introduce an operator to manage the cluster more independently


   */


  // test server to check if this guy is alive
  implicit val am = ActorMaterializer()
  Http().bindAndHandle(get(complete("ACK is alive!")), "0.0.0.0", 8080)

}
