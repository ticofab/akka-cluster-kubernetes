package io.ticofab.akkaclusterkubernetes.actor

import akka.actor.{Actor, ActorLogging, Props}
import io.ticofab.akkaclusterkubernetes.actor.InputSource.Job
import io.ticofab.akkaclusterkubernetes.actor.Router.JobCompleted

class Worker extends Actor with ActorLogging {

  log.info(s"creating worker {}", self.path.name)
  val jobsMillis = 1000 / Worker.jobsRatePerSecond

  // starts a little server to serve an "alive" endpoint
  //  implicit val as = context.system
  //  implicit val am = ActorMaterializer()
  //  val routes = get {
  //    println(s"worker ${self.path.name} got an alive request" )
  //    complete(s"Akka Cluster Kubernetes, worker ${self.path.name} is alive!\n")
  //  }
  //  Http().bindAndHandle(routes, "0.0.0.0", 8080)

  override def receive = {

    case job: Job =>
      log.debug("{}, received job {}", self.path.name, job.number)

      // Simulate a CPU-intensive workload that takes ~2000 milliseconds
      val start = System.currentTimeMillis()
      while ((System.currentTimeMillis() - start) < jobsMillis) {}
      sender ! JobCompleted(job.number, self.path.name)
  }

}

object Worker {
  def apply(): Props = Props(new Worker)

  val jobsRatePerSecond = 0.5
}
