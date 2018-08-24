package io.ticofab.akkaclusterkubernetes

import akka.actor.ActorSystem
import akka.cluster.Cluster
import io.ticofab.akkaclusterkubernetes.common.CustomLogSupport

object AkkaClusterKubernetesWorkerApp extends App with CustomLogSupport {

  implicit val as = ActorSystem("akka-cluster-kubernetes")
  info(logJson(s"Cluster config: ${Config.cluster}"))
  info(logJson(s"This node is a worker"))
  as.actorOf(Worker(), "worker")

  as.registerOnTermination(() => {
    info(logJson(s"Received system termination. Leaving cluster."))
    val cluster = Cluster(as)
    cluster.registerOnMemberRemoved(() => as.terminate())
    cluster.leave(cluster.selfAddress)
  })

}
