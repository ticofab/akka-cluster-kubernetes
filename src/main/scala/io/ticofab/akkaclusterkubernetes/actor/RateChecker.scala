package io.ticofab.akkaclusterkubernetes.actor

import akka.actor.Actor
import akka.stream._
import io.ticofab.akkaclusterkubernetes.actor.RateChecker.{EvaluateRate, Init}
import io.ticofab.akkaclusterkubernetes.actor.Router.Ack

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

class RateChecker extends Actor {
  implicit val as = context.system
  val settings = ActorMaterializerSettings(as).withSupervisionStrategy(_ => Supervision.Restart)
  implicit val am = ActorMaterializer()

  println(s"creating master ${self.path.name}")

  // the router
  val workerRouter = context.actorOf(Router(), "router")

  // the jobs to execute
  var jobs: List[String] = List()

  // state to evaluate rate of message processing
  var completedJobs = 0

  context.system.scheduler.schedule(10.seconds, 10.seconds, self, EvaluateRate)

  override def receive = {
    case s: String =>
      println("received job: " + s)
      jobs = s :: jobs

    case Init =>
      // son is ready!
      println("son is ready")
      workerRouter ! jobs.head
      jobs = jobs.tail

    case Ack =>
      // we can send another one!
      completedJobs += 1
      workerRouter ! jobs.head
      jobs = jobs.tail

    case EvaluateRate =>
      val rate = completedJobs match {
        case 0 => 0.0
        case x => jobs.size.toDouble / x.toDouble
      }
      // TODO: this isn't a real rate
      println(s"${self.path.name}, jobs to do: ${jobs.size}, completed: $completedJobs, rate = $rate")
  }
}

object RateChecker {

  case object EvaluateRate

  case object Init

}

/*

  notes from call on 9 december with Adam

  - we keep it to 1 worker per node, at that point we know exactly how many workers we have
  - each node will have its own pod ---> one pod is one node is one worker
  - only process messages where there is actual chance of processing them, so NOT loose messages!
  - if a message times out, then it should go back in the queue

 */
