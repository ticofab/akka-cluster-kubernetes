package io.ticofab.akkaclusterkubernetes.actor

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

import akka.Done
import akka.actor.Actor

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class Worker extends Actor {

  println(s"creating worker ${self.path.name}")

  override def receive = {
    case s: String =>
      println(s"${self.path.name}, ${LocalDateTime.now.format(DateTimeFormatter.ISO_LOCAL_TIME)}, $s")
      val originalSender = sender
      Future(Thread.sleep(2000)).onComplete(_ => originalSender ! Done)

    //context.system.scheduler.scheduleOnce(2.seconds, originalSender, Done)
  }
}