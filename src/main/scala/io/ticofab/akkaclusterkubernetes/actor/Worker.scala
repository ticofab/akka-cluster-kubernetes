package io.ticofab.akkaclusterkubernetes.actor

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

import akka.Done
import akka.actor.Actor
import io.ticofab.akkaclusterkubernetes.AkkaClusterKubernetesApp.Job
import io.ticofab.akkaclusterkubernetes.actor.Router.JobResult

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class Worker extends Actor {

  println(s"creating worker ${self.path.name}")

  // starts a little server to serve an "alive" endpoint
  //  implicit val as = context.system
  //  implicit val am = ActorMaterializer()
  //  val routes = get {
  //    println(s"worker ${self.path.name} got an alive request" )
  //    complete(s"Akka Cluster Kubernetes, worker ${self.path.name} is alive!\n")
  //  }
  //  Http().bindAndHandle(routes, "0.0.0.0", 8080)

  override def receive = {
    case job: Job =>
      println(s"${self.path.name}, ${LocalDateTime.now.format(DateTimeFormatter.ISO_LOCAL_TIME)}, received $job")
      val originalSender = sender
      val tb = System.currentTimeMillis
      executeJob(job).onComplete(_ => {
        val et = System.currentTimeMillis - tb
        println(s"${self.path.name}, evaluated task in $et milliseconds.")
        originalSender ! JobResult(job, Some(Done))
      })
  }

  def executeJob(job: Job) = Future {
    Thread.sleep(400)
  }
}