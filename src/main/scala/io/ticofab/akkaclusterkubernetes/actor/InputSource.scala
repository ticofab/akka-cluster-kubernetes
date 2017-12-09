package io.ticofab.akkaclusterkubernetes.actor

import akka.actor.{Actor, ActorRef}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.util.Random

/**
  * Actor to provide a source of input messages for the recipient
  *
  * @param target The recipient of our messages
  */
class InputSource(target: ActorRef) extends Actor {
  context.system.scheduler.schedule(0.second, 200.milliseconds, () => {
    target ! Random.alphanumeric.filter(_.isLetter).take(5).mkString
  })

  override def receive = Actor.emptyBehavior
}
