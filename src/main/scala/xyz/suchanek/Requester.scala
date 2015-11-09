package xyz.suchanek

import akka.actor.Actor.Receive
import akka.actor.{Actor, ActorSystem, ActorRef, Props, FSM}
import xyz.suchanek.Requester._
import xyz.suchanek.Implicits._
import spray.can.client.HostConnectorSettings
import akka.io.IO
import spray.can.Http
import spray.can.Http.{ClientConnectionType, HostConnectorInfo}
import spray.http.HttpHeaders.{ModeledHeader, ModeledCompanion}
import spray.http._
import spray.http.HttpMethods._
import xyz.suchanek.ProxyPool._
import scala.concurrent.duration._
import scala.util.Failure

object Requester {

  sealed trait  Response
  final case class DownloadUrl(host: String, uri: String,url: String = null,id: String = null)

  final case class HTMLResponse(data: String, id: String) extends Response

  final case class ErrorResponse(status: StatusCode, id: String) extends Response


  sealed trait State

  case object Idle extends State

  case object RequestedProxy extends State

  case object RequestedHttpSetup extends State

  case object RequestedHttpResponse extends State

  sealed trait Data

  case object Uninitialized extends Data

  final case class Initialized(host: String, uri: String, proxy: String, originalSender: ActorRef, id:String) extends Data

}

class Requester extends FSM[State, Data] {

  def getProxy() = context.system.actorSelection("/user/proxypool")

  startWith(Idle, Uninitialized)

  //Send request for proxy when new urld to download recived
  when(Idle) {
    case Event(d: DownloadUrl, Uninitialized) =>
      val x = if (d.url != null) {
        val u = new java.net.URL(d.url)
        val q = u.getQuery

        val uri = if (q != null) {
          u.getPath + "?" + q
        }
        else u.getPath
        (u.getHost, uri)
      } else {
        (d.host, d.uri)
      }

      getProxy() ! RequestProxyAddress(null)
      println("Sent proxy rq");
      goto(RequestedProxy) using Initialized(host = x._1, uri = x._2, null, sender, d.id)
  }

  //Got proxy address, setup http client
  when(RequestedProxy) {
    case Event(r: ResponseProxyAddress, x: Initialized) =>
      IO(Http) ! Http.HostConnectorSetup(
        x.host,
        port = 80,
        connectionType = ClientConnectionType.Proxied(r.ip, r.port)
      )
      println("Rq conenction setup: " + r.ip + ":" + r.port)
      goto(RequestedHttpSetup) using x.copy(proxy = r.ip + ":" + r.port);

    case Event(NoProxyLeft, x: Initialized) => {
      x.originalSender ! ErrorResponse(StatusCodes.UseProxy, x.id)
      goto(Idle) using Uninitialized
    }
  }

  //Received confirmation of http setup, we can send request
  when(RequestedHttpSetup) {
    case Event(HostConnectorInfo(_, _), x: Initialized) => {
      sender ! HttpRequest(
        GET,
        Uri(x.uri),
        List(
          HttpHeaders.`User-Agent`("Mozilla"),
          HttpHeaders.Accept(MediaRanges.`*/*`),
          new HttpHeader {
            override def value: String = "Keep-Alive"

            override def name: String = "Proxy-Connection"

            override def lowercaseName: String = "proxy-connection"

            override def render[R <: Rendering](r: R): r.type = r ~~ (name + ": " + value)
          }
        )
      )
      println("requested content")
      goto(RequestedHttpResponse) using x
    }
  }


  //Got response from http
  when(RequestedHttpResponse) {
    //OK response - send back html and got idle

    case Event(HttpResponse(StatusCodes.OK, e, h, _), x: Initialized) => {
      println("response ok:" + h)
      x.originalSender ! HTMLResponse(e.data.asString, x.id)
      goto(Idle) using (Uninitialized)
    }

    case Event(HttpResponse(s, _, h, _), x: Initialized) => {
      println(s + ":" + h)
      if (h.filter(_.value.contains(x.host)).isEmpty) {
        println("Proxy error")
        //proxy error (but was able to connect to it) - request a new one
        getProxy() ! RequestProxyAddress(x.proxy)
        goto(RequestedProxy) using x.copy(proxy = null)
      } else {
        //normal error
        println("normal eror")
        x.originalSender ! ErrorResponse(s, x.id)
        goto(Idle) using (Uninitialized)
      }
    }
    //unable to connect to proxy
    case Event(akka.actor.Status.Failure(_), x: Initialized) => {
      println("Proxy error 2")
      //proxy error (but was able to connect to it) - request a new one
      getProxy() ! RequestProxyAddress(x.proxy)
      goto(RequestedProxy) using x.copy(proxy = null)
    }
  }


  whenUnhandled {
    case Event(e, s) =>
      println(e.getClass)
      log.warning("received unhandled request {} in state {}/{}", e, stateName)
      stay
  }

  // unhandled elided ...

  initialize()
}



