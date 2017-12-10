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
  val rateChecker = context.actorOf(Props[RateChecker], "rateChecker")
  context.actorOf(Props(new InputSource(rateChecker)), "inputSource")

  override def receive = Actor.emptyBehavior
}
