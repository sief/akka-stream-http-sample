package ratemonitor

import java.util.concurrent.TimeUnit

import akka.actor._
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpMethods._
import akka.http.scaladsl.model.ws.{TextMessage, UpgradeToWebsocket}
import akka.http.scaladsl.model.{HttpRequest, HttpResponse, Uri}
import akka.stream.scaladsl.{Keep, Sink, Source}
import akka.stream.{ActorMaterializer, OverflowStrategy}
import akka.util.Timeout

import scala.concurrent.ExecutionContext.Implicits.global
import akka.pattern.ask

import scala.concurrent.Future


object Main extends App {

  implicit val system = ActorSystem()

  implicit val materializer = ActorMaterializer()

  implicit val timeout = Timeout(100, TimeUnit.MILLISECONDS)

  private val ServicePath = "/ws-rates"

  // initalize RateMonitor and websocket after DB is set up
  RateDao.setupDB.map { _ =>

    RateMonitor.init()
    val bindingFuture = bindWebsocket()

    println(s"Server online at ws://127.0.0.1:8080$ServicePath\nPress RETURN to stop...")
    scala.io.StdIn.readLine()

    bindingFuture
      .flatMap(_.unbind()) // trigger unbinding from the port
      .onComplete(_ => RateMonitor.shutdown()) // and shutdown when done
  }


  private def bindWebsocket() = {

    def rate2Tm(r: Rate) = TextMessage.Strict( s"""${r.value}; ${r.timestamp}""")

    Http().bindAndHandleAsync({
      case req@HttpRequest(GET, path@(Uri.Path(ServicePath)), _, _, _) =>
        req.header[UpgradeToWebsocket] match {
          case Some(upgrade) =>

            // we can't apply backpressure to real-time events, so let's use a buffer and drop the oldest element in case of overflow
            val (realTimeActorRef, publisher) = Source.actorRef[Rate](1000,
              OverflowStrategy.dropHead).map(rate2Tm).toMat(Sink.publisher)(Keep.both).run()

            (RateMonitor.actorRef ? RateMonitor.Subscribe(realTimeActorRef)).map {case _ =>
              // continue only after subscription is confirmed
              val sink = Sink.onComplete(_ => RateMonitor.actorRef ! RateMonitor.Unsubscribe(realTimeActorRef)) // unsubscribe when client connection closed
              val source = Source(RateDao.getStream.mapResult(rate2Tm)) ++ Source(publisher)
              upgrade.handleMessagesWithSinkSource(sink, source)
            }

          case None => Future.successful(HttpResponse(400, entity = "Not a valid websocket request!"))
        }

      case _: HttpRequest => Future.successful(HttpResponse(404, entity = "Unknown resource!"))
    }, interface = "localhost", port = 8080)
  }
}
