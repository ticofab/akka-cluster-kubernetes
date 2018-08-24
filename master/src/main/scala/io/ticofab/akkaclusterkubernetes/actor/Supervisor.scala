package io.ticofab.akkaclusterkubernetes.actor

import akka.actor.SupervisorStrategy.Restart
import akka.actor.{Actor, OneForOneStrategy, Props}
import io.ticofab.akkaclusterkubernetes.actor.scaling.{DummyScalingController, KubernetesController}
import io.ticofab.akkaclusterkubernetes.common.CustomLogSupport
import io.ticofab.akkaclusterkubernetes.config.Config

class Supervisor extends Actor with CustomLogSupport {

  info(logJson("Supervisor starting"))

  override def supervisorStrategy = OneForOneStrategy() {
    case t: Throwable =>
      error("supervisor, caught exception, restarting failing child", t)
      Restart
  }

  // create scaling controller
  val scalingController = {
    val useK8S = Config.kubernetes.`use-kubernetes`
    val props = if (useK8S) KubernetesController() else Props(new DummyScalingController)
    context.actorOf(props)
  }

  // the master
  val master = context.actorOf(Master(scalingController), "master")

  // the tunable source of jobs
  val jobSource = context.actorOf(JobSource(master), "jobSource")

  // the server
  context.actorOf(Server(jobSource, master))

  override def receive = Actor.emptyBehavior
}

object Supervisor {
  def apply(): Props = Props(new Supervisor)
}
