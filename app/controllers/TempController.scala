package controllers

import javax.inject._
import scala.concurrent.duration._
import play.api._
import play.api.mvc._
import play.api.libs.json.Json
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.Logger
import akka.actor._
import akka.pattern.ask
import akka.util.Timeout
import actors._

@Singleton
class TempController @Inject() (@Named("tmp-actor") tmpActor: ActorRef) extends Controller {

  def temperature = Action.async { implicit request =>
    import actors.TemperatureActor._

    Logger.debug("request for temp")
    implicit val timeout: Timeout = 5.seconds
    (tmpActor ? ReadTemperatures).mapTo[Temperatures].map { temp =>
      temp.ambient.map {
        case a: Double => {
          val fahrenheit = (a * 9/5) + 32
          Logger.debug(s"Actor replied with ambient temp: $a ($fahrenheit F)")
        }
      }
      temp.cpu.map {
        case c: Double => {
          val fahrenheit = (c * 9/5) + 32
          Logger.debug(s"Actor replied with CPU temp: $c ($fahrenheit F)")
        }
      }
      temp.chamber.map {
        case f: Double => {
          val fahrenheit = (f * 9/5) + 32
          Logger.debug(s"Actor replied with chamber temp: $f ($fahrenheit F)")
        }
      }
      Ok(Json.obj(
          "cpu" -> temp.cpu,
          "ambient" -> temp.ambient,
          "chamber" -> temp.chamber
      ))
    }
  }
}
