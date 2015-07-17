package ratemonitor

import java.sql.Timestamp

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpRequest
import akka.stream.ActorMaterializer

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.{Failure, Success}
import scala.concurrent.ExecutionContext.Implicits.global


object RateMonitor {

  implicit val system = ActorSystem()

  implicit val materializer = ActorMaterializer()


  // monitorActor messages
  sealed trait MonitorMessages

  case object CheckRates extends MonitorMessages

  private case class Notify(rate: Rate) extends MonitorMessages

  case class Subscribe(ref: ActorRef) extends MonitorMessages

  case object ConfirmSubscription extends MonitorMessages

  case class Unsubscribe(ref: ActorRef) extends MonitorMessages



  def init() = system.scheduler.schedule(0.second, 5.second, actorRef, CheckRates)
  def shutdown() = system.shutdown()

  val actorRef = system.actorOf(Props(new Actor() {

    private var subscribers = Vector[ActorRef]()

    def receive = {
      case CheckRates =>
        for{
          rateValue <- getRateFromWs
          rate = Rate(new Timestamp(System.currentTimeMillis()), rateValue)
          _ <- RateDao.insertIfDifferentToLast(rate)
        } {
          self ! Notify(rate)
          println("rate changed: " + rate.value)
        }

      case Notify(rate) => subscribers.foreach(_ ! rate)

      case Subscribe(ref) =>
        subscribers = subscribers :+ ref
        sender ! ConfirmSubscription
        println("new monitor subscription, new count: " + subscribers.length)

      case Unsubscribe(ref) =>
        subscribers = subscribers.filterNot(_ == ref)
        println("remove monitor subscription, new count: " + subscribers.length)
    }
  }))

  private def getRateFromWs: Future[BigDecimal] = {
    import spray.json._

    case class RateObj(code: String, name: String, rate: BigDecimal)
    case class RateList(items: List[RateObj])

    object RateProtocol extends DefaultJsonProtocol {
      implicit val rateFormat = jsonFormat3(RateObj)
      implicit object rateListJsonFormat extends RootJsonFormat[RateList] {
        def read(value: JsValue) = RateList(value.convertTo[List[RateObj]])
        def write(f: RateList) = ??? // not used
      }
    }

    import RateProtocol._
    for {
      httpRes <- Http().singleRequest(HttpRequest(uri = "https://bitpay.com/api/rates"))
      string <- httpRes.entity.dataBytes.map(_.utf8String).runFold("")(_ + _)
      rateObjOption = string.parseJson.convertTo[RateList].items.find(_.code == "USD")
      if rateObjOption.isDefined
      res = rateObjOption.get.rate
    } yield res
  }
}
