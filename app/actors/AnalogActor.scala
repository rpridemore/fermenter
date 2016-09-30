package actors

import javax.inject.Inject

import scala.concurrent.Future
import scala.util.Failure
import scala.util.Success

import com.pi4j.system.SystemInfo

import akka.actor.Actor
import akka.actor.Props
import akka.actor.actorRef2Scala
import play.api.Configuration
import play.api.Logger

object AnalogActor {
  def props = Props[AnalogActor]

  case object ReadAmbientTemp
  case class AmbientTemp(ambient: Option[Double], cpu: Option[Double])
}

/**
 * AnalogActor is responsible for data available via the MCP3008 A/D chip
 * for sensors such as the TMP36 temperature sensor.
 */
class AnalogActor @Inject() (configuration: Configuration) extends Actor {
  import AnalogActor._
  import context.dispatcher

  val channel = configuration.getInt("mcp3008.channel.tmp36").getOrElse {
    Logger.warn("Using default channel 0 for TMP36")
    0
  }
  
  val spi = new SpiWrapper

  def receive = {
    case ReadAmbientTemp => {
      val sndr = sender()
      val cpu = Future { readCpuTemp }
      val ambient = Future { readTmp36 }

      val resp = for {
        a <- ambient
        c <- cpu
      } yield AmbientTemp(a, c)

      resp onComplete {
        case Success(t) => {
          Logger.trace("Success!")
          sndr ! t
        }
        case Failure(e) => {
          Logger.trace("Failure!")
          sndr ! AmbientTemp(None, None)
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
}

