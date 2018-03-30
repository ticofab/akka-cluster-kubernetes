package io.ticofab.akkaclusterkubernetes.actor

import java.time.LocalDateTime

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.cluster.Cluster
import akka.cluster.ClusterEvent.{MemberEvent, MemberUp, UnreachableMember}
import akka.cluster.routing.{ClusterRouterGroup, ClusterRouterGroupSettings}
import akka.routing.RoundRobinGroup
import akka.stream.ActorMaterializer
import io.ticofab.akkaclusterkubernetes.actor.InputSource.Job
import io.ticofab.akkaclusterkubernetes.actor.Router.{EvaluateRate, JobCompleted}

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
  val waitingJobs = ListBuffer.empty[Job]
  val submittedJobs = ListBuffer.empty[Job]
  val completedJobs = ListBuffer.empty[(JobCompleted, LocalDateTime)]

  def submitNextJob(): Unit =
    waitingJobs.headOption.foreach(
      job => {
        workersPool ! job
        waitingJobs -= job
        submittedJobs += job
      })

  val rateInterval = 10.seconds
  context.system.scheduler.schedule(rateInterval, rateInterval, self, EvaluateRate)


  override def receive = {

    case MemberUp(m) =>
      // wait a little and trigger a new job
      log.debug(s"a member joined: ${m.address.toString}")
      context.system.scheduler.scheduleOnce(1.second, () => submitNextJob())

    case job: Job =>
      // enqueue the new job
      log.debug("received a new job: {}", job.number)
      waitingJobs += job

    case jobCompleted: JobCompleted =>
      // a job has been completed: trigger the next one
      log.debug("job {} completed", jobCompleted.number)
      completedJobs += ((jobCompleted, LocalDateTime.now))
      submitNextJob()

    case EvaluateRate =>
      val rate = {
        val recentWaitingJobs = waitingJobs filter { job => job.creationDate.isAfter(LocalDateTime.now minusSeconds 10) }
        val recentSubmittedJobs = submittedJobs filter { job => job.creationDate.isAfter(LocalDateTime.now minusSeconds 10) }
        val recentCompletions = completedJobs filter { case (jc, ts) => ts.isAfter(LocalDateTime.now minusSeconds 10) }
        val rate = recentCompletions.size.toDouble / (recentWaitingJobs.size.toDouble + recentSubmittedJobs.size.toDouble)
        log.debug("in the last 10 seconds, we received {} jobs and completed {}. Rate is {}",
          recentWaitingJobs.size + recentSubmittedJobs.size, recentCompletions.size, rate)
        rate
      }

      // we need rate to be slightly above 1.0, say between 1.0 and 1.2
      if (rate < 1.0) {
        log.debug("adding node")
        // there are more incoming jobs than processing speed ---> add node
        // scalingController ! AddNode
      } else if (rate > 1.2) {
        log.debug("removing node")
        // there is too much processing power ---> remove node
        // scalingController ! RemoveNode
      }


      // TODO:
      // if there are workers to consume all incoming messages, the rate will be 1.0. But we still have way too many
      // workers than necessary. So maybe the threshold to scale up is if we are below 1.2 and scale up if b
      // TODO: just think about the rate taking all cases into consideration
  }
}

object Router {
  def apply(scalingController: ActorRef): Props = Props(new Router(scalingController))

  case class JobCompleted(number: Int)

  case object EvaluateRate

}
