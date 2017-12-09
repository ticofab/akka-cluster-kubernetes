package io.ticofab.akkaclusterkubernetes.actor

import akka.actor.{Actor, Props}
import akka.cluster.routing.{ClusterRouterPool, ClusterRouterPoolSettings}
import akka.pattern.ask
import akka.routing.RoundRobinPool
import akka.stream.ActorAttributes.supervisionStrategy
import akka.stream.Supervision.resumingDecider
import akka.stream.scaladsl.{Keep, Sink, Source}
import akka.stream.{ActorMaterializer, ActorMaterializerSettings, OverflowStrategy, Supervision}
import io.ticofab.akkaclusterkubernetes.actor.Master.{EvaluateRate, JobDone}

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
        allowLocalRoutees = false)
    ).props(Props[Worker]),
    name = "workerRouter")

  // state to evaluate rate of message processing
  var enqueuedJobs: scala.collection.mutable.Set[String] = scala.collection.mutable.Set()
  var completedJobs: scala.collection.mutable.Set[String] = scala.collection.mutable.Set()

  context.system.scheduler.schedule(10.seconds, 10.seconds, self, EvaluateRate)

  val processingQueue = Source.queue[String](100, OverflowStrategy.backpressure)
    .mapAsync(2)(msg => (workerRouter ? msg) (3.seconds).mapTo[JobDone])
    .withAttributes(supervisionStrategy(resumingDecider))
    .toMat(Sink.foreach(jobDone => if (enqueuedJobs.contains(jobDone.s)) {
      println(s"${self.path.name}, adding ${jobDone.s} to the completed jobs.")
      completedJobs += jobDone.s
    }))(Keep.left)
    .run()

  override def receive = {
    case s: String =>

      // enqueue for processing
      processingQueue offer s foreach (_ => {
        println(s"${self.path.name}, adding $s to the enqueued jobs.")
        enqueuedJobs += s
      })

    case EvaluateRate =>
      val percentCompleted = completedJobs.size.toDouble / enqueuedJobs.size.toDouble * 100
      println(s"${self.path.name}, enqueuedJobs: ${enqueuedJobs.size}, completed jobs: ${completedJobs.size}")
      println(s"${self.path.name}, over the last 10 seconds, the percentage of completed jobs is $percentCompleted %")
      enqueuedJobs.clear()
      completedJobs.clear()
  }
}

object Master {

  case object EvaluateRate

  case class JobDone(s: String)

}

/*

  notes from call on 9 december with Adam

  - we keep it to 1 worker per node, at that point we know exactly how many workers we have
  - each node will have its own pod ---> one pod is one node is one worker
  - only process messages where there is actual chance of processing them, so NOT loose messages!
  - if a message times out, then it should go back in the queue

 */
