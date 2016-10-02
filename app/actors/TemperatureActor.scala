package actors

import javax.inject.Inject

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.Failure
import scala.util.Success

import akka.actor.Actor
import akka.actor.Props
import akka.event.Logging
import akka.pattern.ask
import akka.util.Timeout
import akka.util.Timeout.durationToTimeout
import play.api.Configuration
import play.api.libs.concurrent.Execution.Implicits.defaultContext


object TemperatureActor {
  val props = Props[TemperatureActor]

  case object ReadTemps
  case class TempReadings(ambient: Option[Double], cpu: Option[Double], chamber: Option[Double])
}


class TemperatureActor @Inject() (configuration: Configuration) extends Actor {
  import context._
  import TemperatureActor._
  import W1Actor._
  import AnalogActor._

  val log = Logging(context.system, this)
  implicit val timeout: Timeout = 4.seconds
  val w1 = configuration.getString("w1.device.filename") match {
    case Some(device) => Some(context.actorOf(W1Actor.props(device), "W1Actor"))
    case None => None
  }
  val channel = configuration.getInt("mcp3008.channel.tmp36")
  val analog = context.actorOf(AnalogActor.props(channel), "AnalogActor")
    

  def receive = {
    case ReadTemps => {
      val sndr = sender()
      val ambient = (analog ? ReadAmbientTemp).mapTo[AmbientTemp]
      val all = if (w1.isDefined) {
        val chamber = (w1.get ? ReadChamberTemp).mapTo[ChamberTemp]
        List(chamber, ambient)
      } else List(ambient)
        
      Future.sequence(all) onComplete {
        case Success(lst) => lst match {
          case (c :: a :: Nil) => {
            val chmbr = c.asInstanceOf[ChamberTemp]
            val amb = a.asInstanceOf[AmbientTemp]
            sndr ! TempReadings(amb.ambient, amb.cpu, chmbr.chamber)
          }
          case (a :: Nil) => {
            val amb = a.asInstanceOf[AmbientTemp]
            sndr ! TempReadings(amb.ambient, amb.cpu, None)
          }
        }
        case Failure(e) => {
          sndr ! TempReadings(None, None, None)
        }
      }
    }
  }

  private def logit(kind: String, celc: Double) {
    val fahr = (celc * 9/5) + 32
    log.debug(s"Received $kind temp of $celc ($fahr F)")
  }
}
