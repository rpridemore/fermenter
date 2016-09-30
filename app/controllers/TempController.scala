package controllers

import scala.concurrent.duration.DurationInt

import akka.actor.ActorRef
import akka.pattern.ask
import akka.util.Timeout
import akka.util.Timeout.durationToTimeout
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton
import play.api.Logger
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.libs.json.Json
import play.api.libs.json.Json.toJsFieldJsValueWrapper
import play.api.mvc.Action
import play.api.mvc.Controller

@Singleton
class TempController @Inject() (@Named("spi-actor") spiActor: ActorRef, @Named("analog-actor") analogActor: ActorRef) extends Controller {

  def temperature = Action.async { implicit request =>
    import actors.SpiActor._
    import actors.AnalogActor._

    Logger.debug("request for temp")
    implicit val timeout: Timeout = 5.seconds
    val chamber = (spiActor ? ReadChamberTemp).mapTo[ChamberTemp]
    val ambient = (analogActor ? ReadAmbientTemp).mapTo[AmbientTemp]

    for {
      ct <- chamber
      at <- ambient
    } yield {
      at.ambient.map {
        case a: Double => {
          val fahrenheit = (a * 9/5) + 32
          Logger.debug(s"Actor replied with ambient temp: $a ($fahrenheit F)")
        }
      }
      at.cpu.map {
        case c: Double => {
          val fahrenheit = (c * 9/5) + 32
          Logger.debug(s"Actor replied with CPU temp: $c ($fahrenheit F)")
        }
      }
      ct.chamber.map {
        case c: Double => {
          val fahrenheit = (c * 9/5) + 32
          Logger.debug(s"Actor replied with chamber temp: $c ($fahrenheit F)")
        }
      }
      Ok(Json.obj(
        "cpu" -> at.cpu,
        "ambient" -> at.ambient,
        "chamber" -> ct.chamber
      ))
    }

  }
}
