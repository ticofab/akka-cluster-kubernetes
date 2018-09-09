package io.ticofab.akkaclusterkubernetes.actor.scaling

import akka.actor.Actor
import io.ticofab.akkaclusterkubernetes.common.CustomLogSupport

class DummyScalingController extends Actor with CustomLogSupport {
  info(logJson("creating dummy controller"))

  override def receive: Receive = {
    case AddNode => info(logJson(s"dummy controller, received AddNode message"))
    case RemoveNode => info(logJson(s"dummy controller, received RemoveNode message"))
  }
}
