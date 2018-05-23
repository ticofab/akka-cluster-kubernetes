package io.ticofab.akkaclusterkubernetes.actor

import java.time.LocalDateTime

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Directives._
import akka.stream.ActorMaterializer
import io.ticofab.akkaclusterkubernetes.actor.JobSource.Job

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

/**
  * Actor to provide a source of input messages for the recipient
  *
  * @param target The recipient of our messages
  */
class JobSource(target: ActorRef) extends Actor with ActorLogging {

  log.info("job source starting, target is {}", target.path.name)

  implicit val as = context.system
  var counter = 0
  val sendingFunction: Runnable = () => {
    counter += 1
    val now = LocalDateTime.now
    target ! Job(counter, now, self)
  }

  // initial rate of two jobs per second
  var cancellableSchedule = as.scheduler.schedule(0.second, 500.milliseconds, sendingFunction)

  // http server to control the rate per second of inputs
  implicit val am = ActorMaterializer()
  val routes = path(IntNumber) {
    jobsPerSecond =>
      // curl http://0.0.0.0:8080/4   --> rate will be 4 jobs per second
      // curl http://0.0.0.0:8080/1   --> rate will be 1 job per second
      // curl http://0.0.0.0:8080/2   --> rate will be 2 jobs per second
      cancellableSchedule.cancel()
      val interval = (1000.0 / jobsPerSecond.toDouble).milliseconds
      cancellableSchedule = as.scheduler.schedule(0.second, interval, sendingFunction)
      complete(s"rate set to 1 message every ${interval.toCoarsest}.\n")
  } ~ get {
    // curl http://0.0.0.0:8080     --> simple health check
    complete("Akka Cluster Kubernetes is alive!\n")
  }

  Http().bindAndHandle(routes, "0.0.0.0", 8080)

  override def receive = Actor.emptyBehavior
}

object JobSource {
  def apply(target: ActorRef): Props = Props(new JobSource(target))

  case class Job(number: Int, creationDate: LocalDateTime, sender: ActorRef)

}
