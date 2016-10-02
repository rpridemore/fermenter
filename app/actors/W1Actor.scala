package actors

import java.io.IOException
import java.nio.charset.Charset
import java.nio.file.FileSystems
import java.nio.file.Files

import scala.concurrent.Future
import scala.util.Failure
import scala.util.Success

import akka.actor.Actor
import akka.actor.Props
import play.api.Configuration
import akka.event.Logging

object W1Actor {
  def props(deviceFile: String) = Props(new W1Actor(deviceFile))

  case object ReadChamberTemp
  case class ChamberTemp(chamber: Option[Double] = None)
}

/**
 * W1Actor is responsible for interacting with the onewire bus
 * for reading temperature values from the DS18B20 waterproof probe.
 */
class W1Actor(deviceFile: String) extends Actor {
  import W1Actor._
  import context.dispatcher

  val log = Logging(context.system, this)
  var w1_device = setupOnewire

  def receive = {
    case ReadChamberTemp => {
      val sndr = sender()
      val response = Future { readChamberTemp } map (ChamberTemp(_))

      response onComplete {
        case Success(t) => {
          log.debug("Success!")
          sndr ! t
        }
        case Failure(e) => {
          log.debug("Failure!")
          sndr ! ChamberTemp
        }
      }
    }
  }

  private def readChamberTemp: Option[Double] = {
    log.debug("reading fermentation chamber temp")
    w1_device match {
      case Some(fname) =>
        try {
          val path = FileSystems.getDefault.getPath(fname)
          val lines = Files.readAllLines(path, Charset.forName("UTF-8"))
          if (lines.size() == 2 && lines.get(0).endsWith("YES")) {
            val second = lines.get(1)
            val indx = second.indexOf("t=")
            val tmp = second.substring(indx + 2).toDouble / 1000
            log.info(s"Chamber temp is $tmp")
            Some(tmp)
          } else {
            log.info("Onewire read failed, trying again...")
            Thread.sleep(200L)
            readChamberTemp
          }
        } catch {
          case e: IOException => {
            log.error("Error reading fermentation temp from onewire", e)
            None
          }
        }
      case _ => None
    }
  }

  private def setupOnewire = {
    log.debug(s"Fermentation chamber temp will be read from $deviceFile")
    val path = FileSystems.getDefault.getPath(deviceFile)
    if (Files.exists(path) && Files.isReadable(path)) {
      Some(deviceFile)
    } else {
      log.warning(s"Onewire device file $deviceFile does not exist or is not readable")
      None
    }
  }
}

