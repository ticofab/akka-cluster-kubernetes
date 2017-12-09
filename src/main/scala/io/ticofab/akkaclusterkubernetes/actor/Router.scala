package io.ticofab.akkaclusterkubernetes.actor

import akka.Done
import akka.actor.{Actor, ActorRef, Props}
import akka.cluster.Cluster
import akka.cluster.ClusterEvent.{MemberEvent, MemberExited, MemberUp, UnreachableMember}
import akka.cluster.routing.{ClusterRouterGroup, ClusterRouterGroupSettings}
import akka.pattern.ask
import akka.routing.RoundRobinGroup
import io.ticofab.akkaclusterkubernetes.actor.RateChecker.Init
import io.ticofab.akkaclusterkubernetes.actor.Router.{Ack, JobResult}

import scala.concurrent.ExecutionContext.Implicits.global
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

  var myUser: Option[ActorRef] = None

  override def receive = empty

  def empty: Receive = {
    case MemberUp(m) =>
      println("the first member joined: " + m.address.toString)
      context.parent ! Init
      context become ready(1)

    case s: String => // do nothing
  }

  def ready(workers: Int): Receive = {
    case MemberUp(m) => context become ready(workers + 1)
    case MemberExited(m) => context become (if (workers == 1) empty else ready(workers + 1))
    case s: String =>
      val ackRecipient = sender
      (workerRouter ? s) (3.seconds).mapTo[JobResult].onComplete {
        case Success(jobResult) => jobResult.outcome match {
          case Some(done) => ackRecipient ! Ack
          case None => // agh!
        }
        case Failure(error) => // agh!
      }
  }
}

object Router {
  def apply(): Props = Props(new Router)

  case class JobResult(outcome: Option[Done])

  case object Ack

  case object Complete

}
