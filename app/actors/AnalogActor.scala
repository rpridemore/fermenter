package actors

import scala.concurrent.Future
import scala.util.Failure
import scala.util.Success

import com.pi4j.system.SystemInfo

import akka.actor.Actor
import akka.actor.Props
import akka.actor.actorRef2Scala
import akka.event.Logging

object AnalogActor {
  def props(channel: Option[Int]) = Props(new AnalogActor(channel))

  case object ReadAmbientTemp
  case class AmbientTemp(ambient: Option[Double], cpu: Option[Double])
}

/**
 * AnalogActor is responsible for data available via the MCP3008 A/D chip
 * for sensors such as the TMP36 temperature sensor.
 */
class AnalogActor(cfgChannel: Option[Int]) extends Actor {
  import AnalogActor._
  import context.dispatcher

  val log = Logging(context.system, this)
  val channel = cfgChannel.getOrElse {
    log.warning("Using default channel 0 for TMP36")
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
          log.debug("Success!")
          sndr ! t
        }
        case Failure(e) => {
          log.debug("Failure!")
          sndr ! AmbientTemp(None, None)
        }
      }
    }
  }

  private def readCpuTemp = {
    log.debug("reading CPU temp")
    val tmp = SystemInfo.getCpuTemperature.asInstanceOf[Double]
    log.info(s"CPU temp is $tmp")
    Some(tmp)
  }

  private def readTmp36 = {
    log.debug("reading TMP36 via SPI")
    val level = spi.readChannel(channel)
    log.debug(s"TMP36 level is $level")
    // convert level to temp in Celcius
    val tmp = ((level * 330.0) / 1023.0) - 50.0
    log.info(s"Ambient temp is $tmp")
    Some(tmp)
  }
}

