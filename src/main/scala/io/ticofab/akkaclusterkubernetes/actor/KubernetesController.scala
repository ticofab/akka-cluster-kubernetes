package io.ticofab.akkaclusterkubernetes.actor

import akka.actor.{Actor, Props}
import io.ticofab.akkaclusterkubernetes.actor.KubernetesController.{AddNode, RemoveNode}

// TODO this guys knows the kubernetes ways
class KubernetesController extends Actor {

  override def receive = {

    case AddNode => ???

    case RemoveNode => ???

  }

}

object KubernetesController {
  def apply(): Props = Props(new KubernetesController)

  case object AddNode

  case object RemoveNode

}
