package io.ticofab.akkaclusterkubernetes.actor.scaling

import akka.actor.Actor
import io.ticofab.akkaclusterkubernetes.common.CustomLogSupport

class DummyScalingController extends Actor with CustomLogSupport {
  info(logJson("creating dummy controller"))

  override def receive: Receive = Actor.ignoringBehavior
}
