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
import actors.TemperatureActor._

@Singleton
class TempController @Inject() (@Named("tmp-actor") tmpActor: ActorRef) extends Controller {

  def temperature = Action.async { implicit request =>
    Logger.debug("request for ambient temp")
    implicit val timeout: Timeout = 5.seconds
    (tmpActor ? ReadTemperatures).mapTo[Temperatures].map { temp =>
      val fahrenheit = (temp.ambient * 9/5) + 32
      Logger.debug(s"Actor replied with $temp (ambient: $fahrenheit F)")
      Ok(Json.obj(
          "cpu" -> temp.cpu,
          "ambient" -> temp.ambient,
          "chamber" -> temp.chamber
      ))
    }
  }
}
