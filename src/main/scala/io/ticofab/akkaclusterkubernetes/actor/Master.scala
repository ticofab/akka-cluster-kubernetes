package io.ticofab.akkaclusterkubernetes.actor

import java.time.LocalDateTime

import akka.Done
import akka.actor.{Actor, Props}
import akka.cluster.routing.{ClusterRouterPool, ClusterRouterPoolSettings}
import akka.pattern.ask
import akka.routing.RoundRobinPool
import akka.stream.scaladsl.{Keep, Sink, Source}
import akka.stream.{ActorMaterializer, ActorMaterializerSettings, OverflowStrategy, Supervision}
import io.ticofab.akkaclusterkubernetes.actor.Master.EvaluateRate

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

class Master extends Actor {
  implicit val as = context.system
  val settings = ActorMaterializerSettings(as).withSupervisionStrategy(_ => Supervision.Restart)
  implicit val am = ActorMaterializer()

  println(s"creating master ${self.path.name}")

  // the router pool
  val workerRouter = context.actorOf(
    ClusterRouterPool(
      RoundRobinPool(0),
      ClusterRouterPoolSettings(
        totalInstances = 100,
        maxInstancesPerNode = 1,
        allowLocalRoutees = true)
    ).props(Props[Worker]),
    name = "workerRouter")

  // state to evaluate rate of message processing
  var inputTimestamps: List[LocalDateTime] = List()
  var doneTimestamps: List[LocalDateTime] = List()
  context.system.scheduler.schedule(0.seconds, 10.seconds, self, EvaluateRate)

  val processingQueue = Source.queue[String](100, OverflowStrategy.dropTail)
    .mapAsyncUnordered(10)(msg => (workerRouter ? msg) (5.seconds).mapTo[Done])
    .recover { case t: Throwable =>
      println(t.getMessage)
      Done
    }.toMat(Sink.foreach { _ =>
      // update the timestamps log of the acknowledgements
      doneTimestamps = LocalDateTime.now :: doneTimestamps
    })(Keep.left)
    .run()

  override def receive = {
    case s: String =>
      // update input messages timestamps log
      inputTimestamps = LocalDateTime.now :: inputTimestamps

      // enqueue for processing
      processingQueue offer s foreach println

    case EvaluateRate =>
      val oneMinuteAgo = LocalDateTime.now minusMinutes 1
      inputTimestamps = inputTimestamps filter (_ isAfter oneMinuteAgo)
      doneTimestamps = doneTimestamps filter (_ isAfter oneMinuteAgo)
      val rate: Double =
        if (inputTimestamps.isEmpty || doneTimestamps.isEmpty) 0.0
        else inputTimestamps.length.toDouble / doneTimestamps.length.toDouble
      println(s"${self.path.name}, evaluated rate: ${inputTimestamps.length} / ${doneTimestamps.length} ===> $rate")
  }
}

object Master {

  case object EvaluateRate

}
