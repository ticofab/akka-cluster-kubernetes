package io.ticofab.akkaclusterkubernetes

import akka.actor.{Actor, Props}
import io.ticofab.akkaclusterkubernetes.common.{CustomLogSupport, Job, JobCompleted}

class Worker extends Actor with CustomLogSupport {

  val jobsMillis = 1000 / Job.jobsRatePerSecond
  val namePort = self.path.name + self.path.address.port
  info(logJson(s"creating worker $namePort"))
  info(logJson(s"each job will take $jobsMillis millis."))

  override def receive = {

    case job: Job =>
      info(logJson(s"worker ${self.path.name}, received job ${job.number}"))

      // Simulate a CPU-intensive workload that takes ~2000 milliseconds
      val start = System.currentTimeMillis()
      while ((System.currentTimeMillis() - start) < jobsMillis) {}

      sender ! JobCompleted(job.number, namePort)
  }

}

object Worker {
  def apply(): Props = Props(new Worker)
}
