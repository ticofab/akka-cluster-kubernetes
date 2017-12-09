package io.ticofab.akkaclusterkubernetes.actor

import akka.actor.Actor
import akka.stream.QueueOfferResult.{Dropped, Enqueued, QueueClosed}
import akka.stream._
import akka.stream.scaladsl.{Keep, Sink, Source}
import io.ticofab.akkaclusterkubernetes.actor.RateChecker.EvaluateRate
import io.ticofab.akkaclusterkubernetes.actor.Router.{Ack, Complete, Init}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.util.{Failure, Success}

class RateChecker extends Actor {
  implicit val as = context.system
  val settings = ActorMaterializerSettings(as).withSupervisionStrategy(_ => Supervision.Restart)
  implicit val am = ActorMaterializer()

  println(s"creating master ${self.path.name}")

  // the router
  val workerRouter = context.actorOf(Router(), "router")

  // state to evaluate rate of message processing
  var enqueuedJobs = 0
  var droppedJobs = 0
  var completedJobs = 0

  context.system.scheduler.schedule(10.seconds, 10.seconds, self, EvaluateRate)

  val processingQueue = Source.queue[String](5, OverflowStrategy.dropNew)
    .toMat(Sink.actorRefWithAck(workerRouter, Init, Ack, Complete))(Keep.left)
    .mapMaterializedValue { f =>
      println("yo")
      f
    }
    .run()

  override def receive = {
    case s: String =>

      // enqueue for processing
      processingQueue offer s onComplete {
        case Success(qor) => qor match {
          case Enqueued =>
            println("job enqueued: " + s)
            enqueuedJobs += 1

          case Dropped =>
            println("job dropped: " + s)
            droppedJobs += 1

          case QueueClosed => println("queue closed")
          case QueueOfferResult.Failure(error) => println("error " + error.getMessage)
        }
        case Failure(error) => println("error " + error.getMessage)
      }

    case EvaluateRate =>
      val rate = completedJobs match {
        case 0 => 0.0
        case x => (enqueuedJobs + droppedJobs).toDouble / x
      }
      println(s"${self.path.name}, rate = $rate")
  }
}

object RateChecker {

  case object EvaluateRate

}

/*

  notes from call on 9 december with Adam

  - we keep it to 1 worker per node, at that point we know exactly how many workers we have
  - each node will have its own pod ---> one pod is one node is one worker
  - only process messages where there is actual chance of processing them, so NOT loose messages!
  - if a message times out, then it should go back in the queue

 */
