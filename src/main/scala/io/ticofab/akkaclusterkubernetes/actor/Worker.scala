package io.ticofab.akkaclusterkubernetes.actor

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

import akka.actor.Actor
import io.ticofab.akkaclusterkubernetes.actor.Master.JobDone

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class Worker extends Actor {

  println(s"creating worker ${self.path.name}")

  override def receive = {
    case s: String =>
      println(s"${self.path.name}, ${LocalDateTime.now.format(DateTimeFormatter.ISO_LOCAL_TIME)}, $s")
      val originalSender = sender
      val tb = System.currentTimeMillis
      doWork(s).onComplete(_ => {
        val et = System.currentTimeMillis - tb
        println(s"${self.path.name}, evaluated task in $et milliseconds.")
        originalSender ! JobDone(s)
      })
  }

  def doWork(s: String) = Future {
    val repetition = 500000000
    (1 to repetition).foreach(i => i + s.length * 50)
    Thread.sleep(2000)
  }
}