package io.ticofab.akkaclusterkubernetes

import akka.actor.ActorSystem
import akka.cluster.Cluster
import com.typesafe.scalalogging.LazyLogging
import io.ticofab.akkaclusterkubernetes.actor.Supervisor
import io.ticofab.akkaclusterkubernetes.config.Config

object AkkaClusterKubernetesApp extends App with LazyLogging {

  implicit val as = ActorSystem("akka-cluster-kubernetes")
  logger.debug("    Cluster config: {}", Config.cluster)
  logger.debug("    Remote config: {}", Config.remote)
  logger.debug("    Kubernetes config: {}", Config.kubernetes)

  if (Config.cluster.roles.contains("seed")) {
    logger.debug("This node is a seed node")

    // create the supervisor actor
    as.actorOf(Supervisor(), "supervisor")
  } else {
    logger.debug("This node is a worker")
  }

  as.registerOnTermination(() => {
    logger.debug("Received system termination. Leaving cluster.")
    val cluster = Cluster(as)
    cluster.registerOnMemberRemoved(() => as.terminate())
    cluster.leave(cluster.selfAddress)
  })

}
