package io.ticofab.akkaclusterkubernetes.actor.scaling

import akka.actor.{Actor, ActorLogging}

class DummyScalingController extends Actor with ActorLogging {
  log.debug("creating dummy controller")

  override def receive = Actor.ignoringBehavior
}
