package io.ticofab.akkaclusterkubernetes.actor

import akka.actor.SupervisorStrategy.Restart
import akka.actor.{Actor, OneForOneStrategy, Props}

class Supervisor extends Actor {

  override def supervisorStrategy = OneForOneStrategy() {
    case t: Throwable =>
      println(s"${self.path.name}, caught $t")
      Restart
  }

  // create mast and input source actors
  val master = context.actorOf(Props[Master], "master")
  context.actorOf(Props(new InputSource(master)), "inputSource")

  override def receive = Actor.emptyBehavior
}
