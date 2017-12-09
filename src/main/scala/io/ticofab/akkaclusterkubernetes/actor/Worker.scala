package io.ticofab.akkaclusterkubernetes.actor

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

import akka.Done
import akka.actor.Actor
import io.ticofab.akkaclusterkubernetes.actor.Router.JobResult

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class Worker extends Actor {

  println(s"creating worker ${self.path.name}")

  override def receive = {
    case s: String =>
      println(s"${self.path.name}, ${LocalDateTime.now.format(DateTimeFormatter.ISO_LOCAL_TIME)}, received $s")
      val originalSender = sender
      val tb = System.currentTimeMillis
      doWork(s).onComplete(_ => {
        val et = System.currentTimeMillis - tb
        println(s"${self.path.name}, evaluated task in $et milliseconds.")
        originalSender ! JobResult(Some(Done))
      })
  }

  def doWork(s: String) = Future {
    Thread.sleep(2000)
  }
}