package io.ticofab.akkaclusterkubernetes.actor

import akka.actor.SupervisorStrategy.Restart
import akka.actor.{Actor, ActorLogging, OneForOneStrategy, Props}
import io.ticofab.akkaclusterkubernetes.actor.scaling.{DummyScalingController, KubernetesController}
import io.ticofab.akkaclusterkubernetes.config.Config

class Supervisor extends Actor with ActorLogging {

  log.debug("Supervisor starting")

  override def supervisorStrategy = OneForOneStrategy() {
    case t: Throwable =>
      log.error(t, "supervisor, caught exception, restarting failing child")
      Restart
  }

  // create scaling controller
  val scalingController =
    if (Config.kubernetes.`use-kubernetes`) context.actorOf(KubernetesController(), "k8s-controller")
    else context.actorOf(Props(new DummyScalingController))

  // the router
  val router = context.actorOf(Router(scalingController), "router")

  // the tunable source of jobs
  context.actorOf(InputSource(router), "inputSource")

  override def receive = Actor.emptyBehavior
}

object Supervisor {
  def apply(): Props = Props(new Supervisor)
}
