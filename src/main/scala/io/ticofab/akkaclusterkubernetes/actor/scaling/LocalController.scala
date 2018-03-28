package io.ticofab.akkaclusterkubernetes.actor.scaling

import akka.actor.{Actor, Props}

class LocalController extends Actor {
  override def receive = {
    case AddNode => ???
    case RemoveNode => ???
  }
}

object LocalController {
  def apply(): Props = Props(new LocalController)
}
