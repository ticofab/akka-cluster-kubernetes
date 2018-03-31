package io.ticofab.akkaclusterkubernetes.actor

import java.time.LocalDateTime

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.cluster.ClusterEvent.{MemberEvent, MemberUp, UnreachableMember}
import akka.cluster.routing.ClusterRouterPool
import akka.cluster.{Cluster, routing}
import akka.routing.RoundRobinPool
import akka.stream.ActorMaterializer
import io.ticofab.akkaclusterkubernetes.actor.InputSource.Job
import io.ticofab.akkaclusterkubernetes.actor.Router.{EvaluateRate, JobCompleted}
import io.ticofab.akkaclusterkubernetes.actor.scaling.AddNode

import scala.collection.mutable.ListBuffer
import scala.concurrent.duration._

class Router(scalingController: ActorRef) extends Actor with ActorLogging {

  implicit val ec = context.dispatcher
  implicit val am = ActorMaterializer()(context)

  Cluster(context.system).subscribe(self, classOf[MemberEvent], classOf[UnreachableMember])

  // TODO
  scalingController ! AddNode

  // the router
  val workersPool = context.actorOf(
    ClusterRouterPool(
      RoundRobinPool(0),
      routing.ClusterRouterPoolSettings(
        totalInstances = 100,
        maxInstancesPerNode = 1,
        allowLocalRoutees = false)
    ).props(Props[Worker]),
    name = "workerRouter")

  // the queue of jobs to run
  val waitingJobs = ListBuffer.empty[Job]
  val jobsArrivedInWindow = ListBuffer.empty[Job]
  var previouslyWaitingJobs: Int = 0
  val submittedJobs = ListBuffer.empty[Job]
  val jobsCompletedInWindows = ListBuffer.empty[JobCompleted]

  def submitNextJob(): Unit =
    waitingJobs.headOption.foreach(
      job => {
        workersPool ! job
        waitingJobs -= job
        submittedJobs += job
      })

  val evaluationWindow = 10.seconds
  var previousDelta = 0
  context.system.scheduler.schedule(evaluationWindow, evaluationWindow, self, EvaluateRate)

  override def receive = {

    case MemberUp(m) =>
      // wait a little and trigger a new job
      log.debug(s"a member joined: ${m.address.toString}")
      context.system.scheduler.scheduleOnce(1.second, () => submitNextJob())

    case job: Job =>
      // enqueue the new job
      waitingJobs += job
      jobsArrivedInWindow += job

    case job: JobCompleted =>
      // a job has been completed: trigger the next one
      log.debug("job {} completed ({})", job.number, job.completer)
      jobsCompletedInWindows += job
      submitNextJob()

    case EvaluateRate =>
      val now = LocalDateTime.now
      val tenSecondsAgo = now minusSeconds evaluationWindow.toSeconds
      val recentWaitingJobs = waitingJobs filter { job => job.creationDate.isAfter(tenSecondsAgo) }
      val recentSubmittedJobs = submittedJobs filter { job => job.creationDate.isAfter(tenSecondsAgo) }
      val arrivedCompletedDelta = jobsArrivedInWindow.size - jobsCompletedInWindows.size

      val rate = jobsCompletedInWindows.size.toDouble / (recentWaitingJobs.size.toDouble + recentSubmittedJobs.size.toDouble)
      log.debug("evaluating rate:")
      log.debug("   waiting jobs:                           {}", waitingJobs.size)
      log.debug("   jobs arrived in window                  {}", jobsArrivedInWindow.size)
      log.debug("   arrivedCompletedDelta                   {}", arrivedCompletedDelta)
      log.debug("   previousDelta                           {}", previousDelta)
      log.debug("   new jobs waiting added in window:       {}", waitingJobs.size - previouslyWaitingJobs)
      log.debug("   jobs submitted in window:               {}", recentSubmittedJobs.size)
      log.debug("   jobs completed in window:               {}", jobsCompletedInWindows.size)

      // we need rate to be slightly above 1.0, say between 1.0 and 1.2
      if (arrivedCompletedDelta > 0) {
        log.debug("adding node")
        // there are more incoming jobs than processing speed ---> add node
        // scalingController ! AddNode
      } else if (arrivedCompletedDelta == 0) {
        if (waitingJobs.size > 0) {
          if (previousDelta < 0 && recentSubmittedJobs.size > 0) {
            log.debug("coming from a burndown situation, removing node")
          } else if (recentSubmittedJobs.size == 0) {
            // we're burning exactly what comes, increase burning
            log.debug("we're burning exactly what comes, adding node")
          }
        }
        // there is too much processing power ---> remove node
        // scalingController ! RemoveNode
      } else {
        // delta < 0
        val windowsToZero = waitingJobs.size / Math.abs(arrivedCompletedDelta)
        log.debug("we're burning down. At this rate it will take about {} windows to complete", windowsToZero)
        if (windowsToZero > 10) {
          log.debug("adding node")
        }
      }

      previouslyWaitingJobs = waitingJobs.size
      previousDelta = arrivedCompletedDelta
      jobsArrivedInWindow.clear()
      jobsCompletedInWindows.clear()


    // TODO:
    // if there are workers to consume all incoming messages, the rate will be 1.0. But we still have way too many
    // workers than necessary. So maybe the threshold to scale up is if we are below 1.2 and scale up if b
    // TODO: just think about the rate taking all cases into consideration

    // TODO: semplification to introduce knowlledge.
    // knowledge of the workers burndown rate
    // knowledge of the time it takes to spin up and down a worker
    // then I can calculate the power that I need and simply wait for a little bit before spinning up more machines.
    // or at that point I directly tell to spin up multiple nodes at one time?
    
  }
}

object Router {
  def apply(scalingController: ActorRef): Props = Props(new Router(scalingController))

  case class JobCompleted(number: Int, completer: String)

  case object EvaluateRate

}
