package io.ticofab.akkaclusterkubernetes.actor

import akka.Done
import akka.actor.{Actor, Props}
import akka.cluster.Cluster
import akka.cluster.ClusterEvent.{MemberEvent, MemberExited, MemberUp, UnreachableMember}
import akka.cluster.routing.{ClusterRouterPool, ClusterRouterPoolSettings}
import akka.pattern.ask
import akka.routing.RoundRobinPool
import io.ticofab.akkaclusterkubernetes.AkkaClusterKubernetesApp.Job
import io.ticofab.akkaclusterkubernetes.actor.Router.{Ack, Init, JobResult, NewJobs}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.{Failure, Success}

class Router extends Actor {
  Cluster(context.system).subscribe(self, classOf[MemberEvent], classOf[UnreachableMember])

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

  override def receive = empty

  def empty: Receive = {
    case MemberUp(m) =>
      println(s"the first member joined: ${m.address.toString}")
      context become ready(1)
      context.system.scheduler.scheduleOnce(1.second, context.parent, Init)
  }

  def ready(workers: Int): Receive = {
    // a member joined the cluster
    case MemberUp(m) =>
      println(s"a new member joined: ${m.address.toString}")
      context become ready(workers + 1)

    // a member left the cluster
    case MemberExited(m) => context become (if (workers == 1) empty else ready(workers - 1))

    // we received new jobs to execute
    case NewJobs(jobs) =>
      // println(s"received ${jobs.size} jobs")
      val ackRecipient = sender
      val seq = Future.sequence(jobs.map(job => (workerRouter ? job) (3.seconds).mapTo[JobResult]))
      seq onComplete {
        case Success(res) => ackRecipient ! Ack(res, workers)
        case Failure(error) => println(error)
      }
  }
}

object Router {
  def apply(): Props = Props(new Router)

  case object Init

  case class NewJobs(jobs: List[Job])

  case class JobsList(jobs: List[Job])

  case class JobResult(job: Job, outcome: Option[Done])

  case class Ack(jobsCompleted: List[JobResult], availableWorkers: Int)

  case object Complete

}
