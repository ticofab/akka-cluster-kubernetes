package io.ticofab.akkaclusterkubernetes.actor

import akka.actor.{Actor, ActorRef, Props}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.ws.{BinaryMessage, Message, TextMessage}
import akka.http.scaladsl.server.Directives.{complete, get, path, _}
import akka.stream.scaladsl.{Flow, GraphDSL, Keep, Sink, Source}
import akka.stream.{ActorMaterializer, FlowShape, OverflowStrategy}
import io.circe.generic.auto._
import io.circe.syntax._
import io.ticofab.akkaclusterkubernetes.actor.Server.{RegisterStatsListener, Stats}
import io.ticofab.akkaclusterkubernetes.common.CustomLogSupport

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

class Server(jobSource: ActorRef, master: ActorRef) extends Actor with CustomLogSupport {
  override def receive = Actor.emptyBehavior

  // http server to control the rate per second of inputs
  implicit val as = context.system
  implicit val am = ActorMaterializer()
  val routes = path(IntNumber) {
    // curl http://0.0.0.0:8080/4   --> rate will be 4 jobs per second
    // curl http://0.0.0.0:8080/1   --> rate will be 1 job per second
    // curl http://0.0.0.0:8080/2   --> rate will be 2 jobs per second
    jobsPerSecond =>
      val interval = (1000.0 / jobsPerSecond.toDouble).milliseconds
      jobSource ! interval
      complete(s"rate set to 1 message every ${interval.toCoarsest}.\n")
  } ~ path("stats") {

    info(logJson("Stats client connected"))
    val (statsSource, publisher) = Source
      .actorRef[Stats](100, OverflowStrategy.dropNew)
      .map(stats => TextMessage(stats.asJson.noSpaces))
      .toMat(Sink.asPublisher(fanout = false))(Keep.both)
      .run()

    master ! RegisterStatsListener(statsSource)

    val handlingFlow = Flow.fromGraph(GraphDSL.create() { implicit b =>
      val msgSink = b.add(Sink.foreach[Message] {
        case tm: TextMessage => tm.textStream.runWith(Sink.ignore)
        case bm: BinaryMessage => bm.dataStream.runWith(Sink.ignore)
      })

      val pubSrc = b.add(Source.fromPublisher(publisher))

      FlowShape(msgSink.in, pubSrc.out)
    })

    handleWebSocketMessages(handlingFlow)
  } ~ get {
    // curl http://0.0.0.0:8080     --> simple health check
    complete("Akka Cluster Kubernetes is alive!\n")
  }

  Http().bindAndHandle(routes, "0.0.0.0", 8080)
}

object Server {
  def apply(jobSource: ActorRef, master: ActorRef): Props = Props(new Server(jobSource, master))

  case class RegisterStatsListener(listener: ActorRef)

  case class Stats(time: Long,
                   queueSize: Int,
                   workersAmount: Int,
                   burndownRate: Int,
                   jobsArrivedInWindow: Int,
                   arrivedCompletedDelta: Int,
                   jobsCompletedInwindow: Int,
                   workersPower: Int)

}
