package io.ticofab.akkaclusterkubernetes.actor

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.cluster.Cluster
import akka.cluster.ClusterEvent.{MemberEvent, MemberUp, UnreachableMember}
import akka.cluster.routing.{ClusterRouterGroup, ClusterRouterGroupSettings}
import akka.routing.RoundRobinGroup
import akka.stream.ActorMaterializer
import io.ticofab.akkaclusterkubernetes.actor.InputSource.Job
import io.ticofab.akkaclusterkubernetes.actor.Router.JobCompleted

import scala.collection.mutable.ListBuffer
import scala.concurrent.duration._

class Router(scalingController: ActorRef) extends Actor with ActorLogging {

  implicit val ec = context.dispatcher
  implicit val am = ActorMaterializer()(context)

  Cluster(context.system).subscribe(self, classOf[MemberEvent], classOf[UnreachableMember])

  // TODO
  // scalingController ! AddNode

  // the router
  val workersPool = context.actorOf(
    ClusterRouterGroup(
      RoundRobinGroup(Nil),
      ClusterRouterGroupSettings(
        totalInstances = 100,
        routeesPaths = List("/user/worker"),
        allowLocalRoutees = false)).props(),
    name = "workerRouter")

  // the queue of jobs to run
  val jobs = ListBuffer.empty[Job]

  def submitNextJob(): Unit =
    jobs.headOption.foreach(
      job => {
        workersPool ! job
        jobs -= job
      })

  override def receive = {

    case MemberUp(m) =>
      // wait a little and trigger a new job
      log.debug(s"a member joined: ${m.address.toString}")
      context.system.scheduler.scheduleOnce(1.second, () => submitNextJob())

    case job: Job =>
      // enqueue the new job
      log.debug("received a new job: {}", job.number)
      jobs += job

    case jobCompleted: JobCompleted =>
      // a job has been completed: trigger the next one
      log.debug("job {} completed", jobCompleted.number)
      submitNextJob()

  }
}

object Router {
  def apply(scalingController: ActorRef): Props = Props(new Router(scalingController))

  case class JobCompleted(number: Int)

}
