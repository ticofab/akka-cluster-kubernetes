package io.ticofab.akkaclusterkubernetes.actor

import akka.actor.{Actor, ActorRef}
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Directives._
import akka.stream.ActorMaterializer

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.util.Random

/**
  * Actor to provide a source of input messages for the recipient
  *
  * @param target The recipient of our messages
  */
class InputSource(target: ActorRef) extends Actor {

  implicit val as = context.system
  val sendingFunction: Runnable = () => {
    target ! Random.alphanumeric.filter(_.isLetter).take(5).mkString
  }

  var cancellableSchedule = as.scheduler.schedule(0.second, 200.milliseconds, sendingFunction)

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
    complete("ACK is alive!\n")
  }

  Http().bindAndHandle(routes, "0.0.0.0", 8080)

  override def receive = Actor.emptyBehavior
}
