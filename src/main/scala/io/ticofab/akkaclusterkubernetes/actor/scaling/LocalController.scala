package io.ticofab.akkaclusterkubernetes.actor.scaling

import akka.actor.{Actor, ActorLogging, Props}

import scala.sys.process.Process
import scala.util.Random

class LocalController extends Actor with ActorLogging {

  case class RunningWorker(process: Process, port: Int)

  var currentWorkers: List[RunningWorker] = List.empty[RunningWorker]

  override def receive = {
    case AddNode =>
      val port = Random.nextInt(10) + 2555
      val process = Process(s"sbt -DPORT=${2553} -DROLES.1=worker run").run()
      log.debug("created new process {}", process)
      currentWorkers = RunningWorker(process, port) :: currentWorkers

    case RemoveNode =>
      val processToKill = currentWorkers.head
      log.debug("killing process {}", processToKill.process)
      currentWorkers = currentWorkers.tail
      processToKill.process.destroy()
  }

  override def postStop(): Unit = {
    // kill any remaining processes
    currentWorkers.foreach(_.process.destroy())
  }
}

object LocalController {
  def apply(): Props = Props(new LocalController)
}
