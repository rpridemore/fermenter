package actors

import java.io.IOException
import java.nio.charset.Charset
import java.nio.file.FileSystems
import java.nio.file.Files
import javax.inject.Inject

import scala.concurrent.Future
import scala.util.Failure
import scala.util.Success

import akka.actor.Actor
import akka.actor.Props
import akka.actor.actorRef2Scala
import play.api.Configuration
import play.api.Logger

object SpiActor {
  def props = Props[SpiActor]

  case object ReadChamberTemp
  case class ChamberTemp(chamber: Option[Double] = None)
}

/**
 * SpiActor is responsible for interacting with the SPI (onewire) bus
 * for reading temperature values from the DS18B20 waterproof probe.
 */
class SpiActor @Inject() (configuration: Configuration) extends Actor {
  import SpiActor._
  import context.dispatcher

  val w1_device = setupOnewire

  def receive = {
    case ReadChamberTemp => {
      val sndr = sender()
      val response = Future { readChamberTemp } map ( ChamberTemp(_) )

      response onComplete {
        case Success(t) => {
          Logger.trace("Success!")
          sndr ! t
        }
        case Failure(e) => {
          Logger.trace("Failure!")
          sndr ! ChamberTemp
        }
      }
    }
  }

  private def readChamberTemp: Option[Double] = {
    Logger.trace("reading fermentation chamber temp")
    w1_device match {
      case Some(fname) =>
        try {
          val path = FileSystems.getDefault.getPath(fname)
          val lines = Files.readAllLines(path, Charset.forName("UTF-8"))
          if (lines.size() == 2 && lines.get(0).endsWith("YES")) {
            val second = lines.get(1)
            val indx = second.indexOf("t=")
            val tmp = second.substring(indx + 2).toDouble / 1000
            Logger.info(s"Chamber temp is $tmp")
            Some(tmp)
          } else {
            Logger.info("Onewire read failed, trying again...")
            Thread.sleep(200L)
            readChamberTemp
          }
        } catch {
          case e: IOException => {
            Logger.error("Error reading fermentation temp from onewire", e)
            None
          }
        }
      case _ => None
    }
  }

  private def setupOnewire = {
    val filename = configuration.getString("w1.device.filename")
    Logger.debug(s"Fermentation chamber temp will be read from $filename")
    filename match {
      case Some(name) => {
        val path = FileSystems.getDefault.getPath(name)
        if (Files.exists(path) && Files.isReadable(path)) {
          Some(name)
        } else {
          Logger.warn(s"Onewire device file $name does not exist or is not readable")
          None
        }
      }
      case None => {
        Logger.warn("Onewire device not configured")
        None
      }
    }
  }
}

