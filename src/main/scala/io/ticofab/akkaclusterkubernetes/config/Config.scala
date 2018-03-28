package io.ticofab.akkaclusterkubernetes.config

import com.typesafe.config.ConfigFactory
import net.ceedubs.ficus.Ficus._
import net.ceedubs.ficus.readers.ArbitraryTypeReader._

object Config {

  case class Remote(hostname: String,
                    port: Int,
                    `bind-hostname`: String,
                    `bind-port`: Int)

  case class Cluster(`seed-nodes`: List[String],
                     roles: List[String])

  case class Kubernetes(`use-kubernetes`: Boolean)

  val config = ConfigFactory.load()
  val remote = config.as[Remote]("akka.remote.netty.tcp")
  val cluster = config.as[Cluster]("akka.cluster")
  val kubernetes = config.as[Kubernetes]("kubernetes")
}
