package xyz.suchanek

import akka.actor.{ActorRef, ActorSystem, Props}


object Implicits {
  implicit val system = ActorSystem("my-actor-system")
  val proxy: ActorRef = system.actorOf(Props[xyz.suchanek.ProxyPool], "proxypool")

}
