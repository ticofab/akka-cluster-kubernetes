package io.ticofab.akkaclusterkubernetes

import com.typesafe.config.ConfigFactory
import net.ceedubs.ficus.Ficus._
import net.ceedubs.ficus.readers.ArbitraryTypeReader._

object Config {
  case class Cluster(`seed-nodes`: List[String])
  val config = ConfigFactory.load()
  val cluster = config.as[Cluster]("akka.cluster")
}
