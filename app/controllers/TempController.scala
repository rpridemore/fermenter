package controllers

import scala.concurrent.duration.DurationInt

import akka.actor.ActorRef
import akka.pattern.ask
import akka.util.Timeout
import akka.util.Timeout.durationToTimeout
import javax.inject.Inject
import javax.inject.Named
import play.api.Logger
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.libs.json.Json
import play.api.libs.json.Json.toJsFieldJsValueWrapper
import play.api.mvc.Action
import play.api.mvc.Controller

class TempController @Inject() (@Named("temp-actor") tempActor: ActorRef) extends Controller {

  def temperature = Action.async { implicit request =>
    import actors.TemperatureActor._

    Logger.debug("request for temp")
    implicit val timeout: Timeout = 5.seconds
    (tempActor ? ReadTemps).mapTo[TempReadings].map { temps =>
      Ok(Json.obj(
        "cpu" -> temps.cpu,
        "ambient" -> temps.ambient,
        "chamber" -> temps.chamber
      ))
    }
  }
}
