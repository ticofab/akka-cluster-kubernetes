package io.ticofab.akkaclusterkubernetes.actor

import java.time.LocalDateTime

import akka.actor.Actor
import akka.stream._
import io.ticofab.akkaclusterkubernetes.AkkaClusterKubernetesApp.Job
import io.ticofab.akkaclusterkubernetes.actor.RateChecker.EvaluateRate
import io.ticofab.akkaclusterkubernetes.actor.Router.{Ack, Init, NewJobs}

import scala.collection.immutable.Queue
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

class RateChecker extends Actor {
  implicit val as = context.system
  val settings = ActorMaterializerSettings(as).withSupervisionStrategy(_ => Supervision.Restart)
  implicit val am = ActorMaterializer()

  println("creating rate checker")

  // the router
  val workerRouter = context.actorOf(Router(), "router")

  // the jobs to execute
  var jobs: Queue[(Job, LocalDateTime)] = Queue()

  // state to evaluate rate of message processing
  var completedJobs: Set[(Job, LocalDateTime)] = Set()

  context.system.scheduler.schedule(10.seconds, 10.seconds, self, EvaluateRate)

  override def receive = {
    case job: Job =>
      // TODO discard messages if queue already too big or something
      jobs = jobs.enqueue((job, LocalDateTime.now))

    case Init =>
      // son is ready!
      println("son is ready")
      workerRouter ! NewJobs(List(jobs.head._1))
      jobs = jobs.drop(1)

    case Ack(jobResults, availableWorkers) =>
      // we can send more jobs!
      println(s"received ack for ${jobResults.size} jobs, available workers: $availableWorkers")
      // TODO: filter out failed jobs
      completedJobs ++= jobResults.map(jr => (jr.job, LocalDateTime.now))
      workerRouter ! NewJobs(jobs.take(availableWorkers).map(_._1).toList)
      jobs = jobs.drop(availableWorkers)

    case EvaluateRate =>
      // calculate rate at which we are processing jobs

      // TODO cases where there are no jobs available
      val rate = {
        val recentJobs = jobs filter { case (s, ts) => ts.isAfter(LocalDateTime.now minusSeconds 10) }
        val recentCompletions = completedJobs filter { case (s, ts) => ts.isAfter(LocalDateTime.now minusSeconds 10) }
        if (recentJobs.isEmpty || recentCompletions.isEmpty) -1.0
        else recentCompletions.size.toDouble / recentJobs.size.toDouble
      }

      // TODO: we need rate to be slightly above 1.0
      // TODO: if rate < 1.0, there are more incoming jobs than processing speed ---> add node
      // TODO: if rate > 1.2, there is too much processing power ---> remove node

      println(s"${self.path.name}, jobs in queue: ${jobs.size}")
      println(s"${self.path.name}, processing rate: $rate")
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
