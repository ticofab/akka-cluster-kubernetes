package io.ticofab.akkaclusterkubernetes.actor

import akka.Done
import akka.actor.{Actor, Props}
import akka.cluster.Cluster
import akka.cluster.ClusterEvent.{MemberEvent, MemberExited, MemberUp, UnreachableMember}
import akka.cluster.routing.{ClusterRouterGroup, ClusterRouterGroupSettings}
import akka.pattern.ask
import akka.routing.RoundRobinGroup
import io.ticofab.akkaclusterkubernetes.AkkaClusterKubernetesApp.Job
import io.ticofab.akkaclusterkubernetes.actor.RateChecker.Init
import io.ticofab.akkaclusterkubernetes.actor.Router.{Ack, JobResult, NewJobs}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.{Failure, Success}

class Router extends Actor {
  Cluster(context.system).subscribe(self, classOf[MemberEvent], classOf[UnreachableMember])

  val workerRouter = context.actorOf(
    ClusterRouterGroup(
      RoundRobinGroup(Nil),
      ClusterRouterGroupSettings(
        totalInstances = 100,
        routeesPaths = List("/user/worker"),
        allowLocalRoutees = false)).props(),
    name = "workerRouter")

  override def receive = empty

  def empty: Receive = {
    case MemberUp(m) =>
      println("the first member joined: " + m.address.toString)
      context.parent ! Init
      context become ready(1)
  }

  def ready(workers: Int): Receive = {
    // a member joined the cluster
    case MemberUp(m) => context become ready(workers + 1)

    // a member left the cluster
    case MemberExited(m) => context become (if (workers == 1) empty else ready(workers - 1))

    // we received new jobs to execute
    case NewJobs(jobs) =>
      println(s"received ${jobs.size} jobs")
      val ackRecipient = sender
      val seq = Future.sequence(jobs.map(job => (workerRouter ? job) (3.seconds).mapTo[JobResult]))
      seq onComplete {
        case Success(res) => ackRecipient ! Ack(res, workers)
        case Failure(error) => ??? // TODO
      }
  }
}

object Router {
  def apply(): Props = Props(new Router)

  case class NewJobs(jobs: List[Job])

  case class JobsList(jobs: List[Job])

  case class JobResult(job: Job, outcome: Option[Done])

  case class Ack(jobsCompleted: List[JobResult], availableWorkers: Int)

  case object Complete

}
