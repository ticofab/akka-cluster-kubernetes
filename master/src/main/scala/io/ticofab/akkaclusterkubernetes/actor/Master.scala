package io.ticofab.akkaclusterkubernetes.actor

import java.time.{LocalDateTime, ZoneOffset}

import akka.actor.{Actor, ActorRef, Props}
import akka.cluster.Cluster
import akka.cluster.ClusterEvent.{MemberEvent, MemberExited, MemberUp, UnreachableMember}
import akka.cluster.routing.{ClusterRouterGroup, ClusterRouterGroupSettings}
import akka.routing.RoundRobinGroup
import akka.stream.ActorMaterializer
import io.ticofab.akkaclusterkubernetes.actor.Master.EvaluateRate
import io.ticofab.akkaclusterkubernetes.actor.Server.{RegisterStatsListener, Stats}
import io.ticofab.akkaclusterkubernetes.actor.scaling.{AddNode, RemoveNode}
import io.ticofab.akkaclusterkubernetes.common.{CustomLogSupport, Job, JobCompleted}

import scala.collection.mutable.ListBuffer
import scala.concurrent.duration._

class Master(scalingController: ActorRef) extends Actor with CustomLogSupport {

  implicit val ec = context.dispatcher
  implicit val am = ActorMaterializer()(context)

  Cluster(context.system).subscribe(self, classOf[MemberEvent], classOf[UnreachableMember])

  // the router
  val workersPool = context.actorOf(
    ClusterRouterGroup(
      RoundRobinGroup(Nil),
      ClusterRouterGroupSettings(
        totalInstances = 100,
        routeesPaths = List("/user/worker"),
        allowLocalRoutees = false)).props(),
    name = "workerRouter")

  // the stats listeners
  var listeners: Set[ActorRef] = Set()

  // the queue of jobs to run
  val waitingJobs = ListBuffer.empty[Job]
  val jobsArrivedInWindow = ListBuffer.empty[Job]
  val jobsCompletedInWindows = ListBuffer.empty[JobCompleted]

  def submitNextJob(): Unit =
    waitingJobs.headOption.foreach(
      job => {
        workersPool ! job
        waitingJobs -= job
      })

  val evaluationWindow = 5.seconds
  val actionInterval = 20
  val singleWorkerPower = (Job.jobsRatePerSecond * evaluationWindow.toSeconds).toInt
  context.system.scheduler.schedule(evaluationWindow, evaluationWindow, self, EvaluateRate)

  // initial value of last action taken, simulating it happened in the past
  var lastActionTaken: LocalDateTime = LocalDateTime.now minusSeconds 60
  var workers: Int = 0

  override def receive: Receive = {

    case MemberUp(m) =>
      // wait a little and trigger a new job
      info(logJson(s"a member joined: ${m.address.toString}"))
      workers += 1
      context.system.scheduler.scheduleOnce(1.second, () => submitNextJob())

    // a member left the cluster
    case MemberExited(_) => workers -= 1

    case UnreachableMember(_) => workers -= 1

    case RegisterStatsListener(listener) => listeners = listeners + listener

    case job: Job =>
      // enqueue the new job
      waitingJobs += job
      jobsArrivedInWindow += job

    case job@JobCompleted(number, name) =>
      // a job has been completed: trigger the next one
      info(logJson(s"job $number completed by $name."))
      jobsCompletedInWindows += job
      if (workers > 0) submitNextJob()

    case EvaluateRate =>
      val now = LocalDateTime.now
      val arrivedCompletedDelta = jobsArrivedInWindow.size - jobsCompletedInWindows.size

      val workerPoolPower = singleWorkerPower * workers
      val difference = jobsArrivedInWindow.size - workerPoolPower
      val possibleToTakeAction = now isAfter (lastActionTaken plusSeconds actionInterval)

      info(logJson("evaluating rate:"))
      info(logJson(s"   queue size:                ${waitingJobs.size}"))
      info(logJson(s"   workers amount:            $workers"))
      info(logJson(s"   burndown rate:             $difference"))
      info(logJson(s"   time:                      ${now.toString}"))
      info(logJson(s"   jobs arrived in window:    ${jobsArrivedInWindow.size}"))
      info(logJson(s"   arrivedCompletedDelta:     $arrivedCompletedDelta"))
      info(logJson(s"   jobs completed in window:  ${jobsCompletedInWindows.size}"))
      info(logJson(s"   workers power:             $workerPoolPower"))
      info(logJson(s"   possible to take action:   $possibleToTakeAction"))

      val stats = Stats(
        now.toEpochSecond(ZoneOffset.UTC),
        waitingJobs.size,
        workers,
        difference,
        jobsArrivedInWindow.size,
        arrivedCompletedDelta,
        jobsCompletedInWindows.size,
        workerPoolPower)

      listeners.foreach(_ ! stats)

      // uncommenting the next line output logs in a CSV-friendly format
      //      log.info("   csv {}", now.toString + "," + waitingJobs.size / 10 + "," + jobsArrivedInWindow.size +
      //        "," + arrivedCompletedDelta + "," + workers + "," + workerPoolPower)

      if (possibleToTakeAction) {

        if (difference > 0) {

          // we are receiving more jobs than we can handle.

          info(logJson("   we need more power, add node"))
          scalingController ! AddNode
          lastActionTaken = now

        } else if (difference == 0) {

          // we are burning as much as it comes.

          // but do we have older jobs to burn?
          if (waitingJobs.size > singleWorkerPower) {
            info(logJson("   we're burning as much as we receive, but we have a long queue. add worker."))
            scalingController ! AddNode
            lastActionTaken = now
          } else {
            // it seems we're at a perfect balance!
            info(logJson("   we're burning as much as we receive, and it seems we don't need more power. Excellent!."))
          }

        } else if (difference < 0) {

          // we are burning down jobs!

          if (waitingJobs.size <= singleWorkerPower) {

            // we are close to the optimal.

            // did we strike a balance or do we have too much power?
            if (Math.abs(difference) <= singleWorkerPower) {
              info(logJson("   we have a little more processing power than we need. stay like this."))
            } else {
              info(logJson("   we have too much power for what we need. remove worker"))
              scalingController ! RemoveNode
              lastActionTaken = now
            }

          } else {

            // we are burning down stuff and need to keep burning.

            // are we burning fast enough?
            if (waitingJobs.size > singleWorkerPower * 4) {

              info(logJson("   we are burning the old queue but not fast enough. add worker."))
              scalingController ! AddNode
              lastActionTaken = now

            } else {
              info(logJson("   we have more power than we need but still burning down old jobs. stay like this."))
            }

          }

        }

      }

      jobsArrivedInWindow.clear()
      jobsCompletedInWindows.clear()

  }
}

object Master {
  def apply(scalingController: ActorRef): Props = Props(new Master(scalingController))

  case object EvaluateRate

}
