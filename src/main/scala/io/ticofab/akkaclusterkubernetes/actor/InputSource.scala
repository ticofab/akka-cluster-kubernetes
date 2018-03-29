package io.ticofab.akkaclusterkubernetes.actor

import java.time.LocalDateTime

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Directives._
import akka.stream.ActorMaterializer
import io.ticofab.akkaclusterkubernetes.actor.InputSource.Job

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

/**
  * Actor to provide a source of input messages for the recipient
  *
  * @param target The recipient of our messages
  */
class InputSource(target: ActorRef) extends Actor with ActorLogging {

  log.info("input source starting, target it {}", target.path.name)

  implicit val as = context.system
  var counter = 0
  val sendingFunction: Runnable = () => {
    counter += 1
    val now = LocalDateTime.now
    target ! Job(counter, now, self)
  }

  var cancellableSchedule = as.scheduler.schedule(0.second, 1000.milliseconds, sendingFunction)

  // http server to control the rate per second of inputs. for instance:
  // curl http://0.0.0.0:8080/4   --> rate will be 4 inputs/s, once every 250 ms
  // curl http://0.0.0.0:8080/500 --> rate will be 500 inputs/s, once every 2 ms
  // curl http://0.0.0.0:8080     --> simple health check
  implicit val am = ActorMaterializer()
  val routes = path(IntNumber) {
    inputsPerSecond =>
      cancellableSchedule.cancel()
      val interval = (1000.0 / inputsPerSecond.toDouble).milliseconds
      cancellableSchedule = as.scheduler.schedule(0.second, interval, sendingFunction)
      complete(s"rate set to 1 message every $interval.\n")
  } ~ get {
    complete("Akka Cluster Kubernetes is alive!\n")
  }

  Http().bindAndHandle(routes, "0.0.0.0", 8080)

  override def receive = Actor.emptyBehavior
}

object InputSource {
  def apply(target: ActorRef): Props = Props(new InputSource(target))

  case class Job(number: Int, creationDate: LocalDateTime, sender: ActorRef)

}
