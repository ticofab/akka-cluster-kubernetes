package io.ticofab.akkaclusterkubernetes.common

import java.time.LocalDateTime

import akka.actor.ActorRef

case class Job(number: Int, creationDate: LocalDateTime, sender: ActorRef)

object Job {
  // how many jobs are completed in a second?
  val jobsRatePerSecond = 1
}