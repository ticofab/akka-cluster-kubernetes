package io.ticofab.akkaclusterkubernetes.common


import java.time.format.DateTimeFormatter
import java.time.{Instant, LocalDateTime, ZoneId}
import java.util.Properties

import wvlet.log.{LogFormatter, LogLevel, LogSupport, Logger}

trait CustomLogSupport extends LogSupport {
  Logger.setDefaultFormatter(LogFormatter.BareFormatter)

  // say NO to java mutable object
  val logLevels = new Properties()
  logLevels.setProperty("sun.net.www.protocol.http", LogLevel.OFF.toString)
  logLevels.setProperty("com.google.api.client.http", LogLevel.OFF.toString)
  logLevels.setProperty("com.google.datastore.v1.client", LogLevel.OFF.toString)

  sealed trait Severity
  case object ERROR extends Severity
  case object INFO extends Severity

  Logger.setLogLevels(logLevels)

  private def mapToString(fields: Map[String, String]) = {
    val list = fields.map { case (key, value) => "\"" + key + "\" : \"" + value + "\"" }.toList
    "{" + list.mkString(",") + "}"
  }

  def logJson(s: String, sev: Severity = INFO) = mapToString(Map("msg" -> s, "severity" -> sev.toString))

  def jsonErrorLog(timestamp: Long, msg: String) = {
    val readableDate = getReadableDate(timestamp)
    "{\"date\" : \"" + readableDate + "\", \"error\" : \"" + msg + "\"}"
  }

  private def getReadableDate(timestamp: Long) = LocalDateTime
    .ofInstant(Instant.ofEpochMilli(timestamp), ZoneId.systemDefault())
    .format(DateTimeFormatter.ISO_DATE_TIME)
}