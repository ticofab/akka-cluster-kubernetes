package io.ticofab.akkaclusterkubernetes.actor

import akka.actor.{Actor, ActorRef}

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

/**
  * Actor to provide a source of input messages for the recipient
  *
  * @param target The recipient of our messages
  */
class InputSource(target: ActorRef) extends Actor {
  context.system.scheduler.schedule(1.second, 1.second, target, "hello")

  override def receive = Actor.emptyBehavior
}
