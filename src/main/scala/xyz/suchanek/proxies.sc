import xyz.suchanek.Requester.{Response, DownloadUrl}
import xyz.suchanek._
import scalaz._
import Scalaz._
import scalaz.OptionT._
import akka.actor
import xyz.suchanek.parsers._
import spray.can.Http
import spray.http.HttpRequest
import spray.http._
import spray.http.HttpMethods._
import scala.concurrent.Future
import akka.io.IO

//import scala.concurrent.ExecutionContext

import scala.concurrent.duration._
import akka.pattern.{ask, pipe}
import akka.util.Timeout
import scala.concurrent.ExecutionContext.Implicits.global

import xyz.suchanek.ProxyPool._
import scala.util.{Failure, Success}

// The ExecutionContext that will be used

import akka.actor.{ActorSystem, ActorRef, Props, Actor}

implicit val system = actor.ActorSystem("my-actor-system")
val proxy: ActorRef = system.actorOf(Props[xyz.suchanek.ProxyPool], "proxypool")
implicit val timeout = Timeout(50 seconds)
val r: ActorRef = system.actorOf(Props[Requester], "requester")


val response: Future[HttpResponse] =
  (IO(Http) ? HttpRequest(GET, Uri("http://www.xroxy.com/proxylist.php?port=&type=Transparent&ssl=&country=&latency=&reliability=&sort=reliability&desc=true&pnum=0"))).mapTo[HttpResponse]


val f = response.map({
  case HttpResponse(StatusCodes.OK, e, _, _) =>
    Xroxy.getProxyList(e.data.asString)
  case _ => None
})

optionT(f).map(
  {
    x:List[(String,Int)] => x.map(
      z => proxy ! AddProxy(z._1, z._2)
    )
  }
)


val content: Future[Response] = (r ? DownloadUrl("checkip.dyndns.com","/")).mapTo[Response]
content.map(println)


