package xyz.suchanek

import akka.actor.{Identify, Actor}

import scala.collection.mutable

import ProxyPool._

object ProxyPool {

  case class RequestProxyAddress(old: String)

  case class ResponseProxyAddress(ip: String, port: Integer);

  case class AddProxy(ip: String, port: Integer)

  case object NoProxyLeft

}

class ProxyPool extends Actor {

  def now() = (System.currentTimeMillis / 1000).toInt

  final private val proxyDeadTime = 5 * 60

  var current: Int = 1
  val proxies = mutable.Set(
    "136.0.16.217:7808"
  )

  val dead = mutable.HashMap[String, Int]()

  override def receive: Receive = {
    case RequestProxyAddress(x) =>
      println(proxies)
      if (x != null) {
        proxies.remove(x)
        dead.update(x, now())
      }

      if (current > proxies.size) {
        current = 1
      }

      if (proxies.size == 0) {
        sender ! NoProxyLeft
      } else {
        val result = proxies.take(current).last

        val addr = result.split(":")
        println(addr(0))
        sender ! ResponseProxyAddress(addr(0), addr(1).toInt)
        current += 1
      }

    case AddProxy(ip, port) =>
      val s = ip + ":" + port
      dead
        .get(s)
        .map(x => now() - x > proxyDeadTime)
      match {
        case Some(true) => {

          dead.remove(s)
        }
        case None => proxies.add(s)
        case _ => println("Wont add new proxy:" + s)
      }

    case Identify =>
      println(self)
  }
}