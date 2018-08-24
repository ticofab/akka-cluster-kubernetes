package io.ticofab.akkaclusterkubernetes

import akka.actor.ActorSystem
import akka.cluster.Cluster
import io.ticofab.akkaclusterkubernetes.actor.Supervisor
import io.ticofab.akkaclusterkubernetes.common.CustomLogSupport
import io.ticofab.akkaclusterkubernetes.config.Config

object AkkaClusterKubernetesMasterApp extends App with CustomLogSupport {

  implicit val as = ActorSystem("akka-cluster-kubernetes")
  info(logJson(s"Cluster config: ${Config.cluster}"))
  info(logJson(s"Remote config: ${Config.remote}"))
  info(logJson(s"Kubernetes config: ${Config.kubernetes}"))
  info(logJson(s"This node is a seed node"))

  // create the supervisor actor
  as.actorOf(Supervisor(), "supervisor")

  as.registerOnTermination(() => {
    info(logJson("Received system termination. Leaving cluster."))
    val cluster = Cluster(as)
    cluster.registerOnMemberRemoved(() => as.terminate())
    cluster.leave(cluster.selfAddress)
  })

}
