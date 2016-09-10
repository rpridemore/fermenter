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


object TemperatureActor {
  def props = Props[TemperatureActor]

  case class ReadTemperatures()
  case class Temperatures(cpu: Double, ambient: Double, chamber: Option[Double])
}


class TemperatureActor @Inject() (configuration: Configuration) extends Actor {
  import context.dispatcher
  import TemperatureActor._

  val channel = configuration.getInt("mcp3008.channel.tmp36").getOrElse(0)
  val w1_device = setupOnewire
  val spi = new SpiWrapper

  def receive = {
    case ReadTemperatures => {
      sender() ! Temperatures(readCpuTemp, readTmp36, readChamberTemp)
    }
  }

  private def readCpuTemp = SystemInfo.getCpuTemperature.asInstanceOf[Double]

  private def readTmp36 = {
    Logger.trace("reading TMP36 via SPI")
    val level = spi.readChannel(channel)
    Logger.debug(s"level is $level")
    // convert level to temp in Celcius
    ((level * 330.0) / 1023.0) - 50.0
  }

  private def readChamberTemp : Option[Double] = {
    try {
      val path = FileSystems.getDefault.getPath(w1_device)
      val lines = Files.readAllLines(path, Charset.forName("UTF-8"))
      if (lines.size() == 2 && lines.get(0).endsWith("YES")) {
        val second = lines.get(1)
        val indx = second.indexOf("t=")
        Some(second.substring(indx + 2).toDouble / 1000)
      } else {
        Thread.sleep(200L)
        readChamberTemp
      }
    }
    catch { case e: IOException => None }
  }

  private def setupOnewire = {
    val filename = configuration.getString("w1.device.filename")
    filename match {
      case Some(name) => {
        val path = FileSystems.getDefault.getPath(name)
        if (Files.exists(path) && Files.isReadable(path)) {
          name
        } else {
          Logger.warn(s"Onewire device file $name does not exist or is not readable")
          ""
        }
      }
      case None => {
        Logger.warn("Onewire device not configured")
        ""
      }
    }
  }
}