package io.ticofab.akkaclusterkubernetes.actor

import akka.actor.{Actor, ActorLogging, Props}
import io.ticofab.akkaclusterkubernetes.actor.JobSource.Job
import io.ticofab.akkaclusterkubernetes.actor.Master.JobCompleted

class Worker extends Actor with ActorLogging {

  val jobsMillis = 1000 / Worker.jobsRatePerSecond
  log.info(s"creating worker {}, each job will take {} millis.", self.path.name, jobsMillis)

  override def receive = {

    case job: Job =>
      log.debug("worker {}, received job {}", self.path.name, job.number)

      // Simulate a CPU-intensive workload that takes ~2000 milliseconds
      val start = System.currentTimeMillis()
      while ((System.currentTimeMillis() - start) < jobsMillis) {}

      sender ! JobCompleted(job.number, self.path.name)
  }

}

object Worker {
  def apply(): Props = Props(new Worker)

  // how many jobs are completed in a second?
  val jobsRatePerSecond = 1
}
