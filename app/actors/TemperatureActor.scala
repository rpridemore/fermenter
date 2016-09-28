package actors

import scala.concurrent.{ Await, Future }
import akka.actor._
import play.api.Configuration
import play.api.Logger
import com.pi4j.io.spi._
import com.pi4j.system.SystemInfo
import java.io._
import java.nio.file._
import javax.inject.Inject
import java.nio.charset.Charset
import scala.util.Success
import scala.util.Failure

object TemperatureActor {
  def props = Props[TemperatureActor]

  case object ReadTemperatures
  case class Temperatures(cpu: Option[Double], ambient: Option[Double], chamber: Option[Double])
}

class TemperatureActor @Inject() (configuration: Configuration) extends Actor {
  import context.dispatcher
  import TemperatureActor._

  val channel = configuration.getInt("mcp3008.channel.tmp36").getOrElse {
    Logger.warn("Using default channel 0 for TMP36")
    0
  }
  val w1_device = setupOnewire
  val spi = new SpiWrapper

  def receive = {
    case ReadTemperatures => {
      val sndr = sender()
      val fermenter = Future { readChamberTemp }
      val cpu = Future { readCpuTemp }
      val ambient = Future { readTmp36 }

      val resp = for {
        f <- fermenter
        c <- cpu
        a <- ambient
      } yield Temperatures(c, a, f)

      resp onComplete {
        case Success(t) => {
          Logger.trace("Success!")
          sndr ! t
        }
        case Failure(e) => {
          Logger.trace("Failure!")
          sndr ! Temperatures(None, None, None)
        }
      }
    }
  }

  private def readCpuTemp = {
    Logger.trace("reading CPU temp")
    val tmp = SystemInfo.getCpuTemperature.asInstanceOf[Double]
    Logger.info(s"CPU temp is $tmp")
    Some(tmp)
  }

  private def readTmp36 = {
    Logger.trace("reading TMP36 via SPI")
    val level = spi.readChannel(channel)
    Logger.debug(s"TMP36 level is $level")
    // convert level to temp in Celcius
    val tmp = ((level * 330.0) / 1023.0) - 50.0
    Logger.info(s"Ambient temp is $tmp")
    Some(tmp)
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