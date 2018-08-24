package io.ticofab.akkaclusterkubernetes.actor

import java.time.LocalDateTime

import akka.actor.{Actor, ActorRef, Props}
import io.ticofab.akkaclusterkubernetes.common.{CustomLogSupport, Job}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

/**
  * Actor to provide a source of input messages for the recipient
  *
  * @param target The recipient of our messages
  */
class JobSource(target: ActorRef) extends Actor with CustomLogSupport {

  info(logJson(s"job source starting, target is ${target.path.name}"))

  implicit val as = context.system
  var counter = 0
  val sendingFunction: Runnable = () => {
    counter += 1
    val now = LocalDateTime.now
    target ! Job(counter, now, self)
  }

  // initial rate of two jobs per second
  var cancellableSchedule = as.scheduler.schedule(0.second, 500.milliseconds, sendingFunction)

  override def receive = {
    case interval: FiniteDuration =>
      cancellableSchedule.cancel()
      cancellableSchedule = as.scheduler.schedule(0.second, interval, sendingFunction)
  }
}

object JobSource {
  def apply(target: ActorRef): Props = Props(new JobSource(target))
}
