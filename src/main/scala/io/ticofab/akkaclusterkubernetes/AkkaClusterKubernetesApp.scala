package io.ticofab.akkaclusterkubernetes

import akka.actor.ActorSystem
import com.typesafe.scalalogging.LazyLogging
import io.ticofab.akkaclusterkubernetes.actor.Supervisor
import io.ticofab.akkaclusterkubernetes.config.Config

object AkkaClusterKubernetesApp extends App with LazyLogging {

  implicit val as = ActorSystem("akka-cluster-kubernetes")
  val roles = Config.cluster.roles

  if (roles.contains("seed")) {
    logger.debug("This node is a seed node")
    as.actorOf(Supervisor(), "supervisor")
  } else {
    logger.debug("This node is a worker")
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

}
