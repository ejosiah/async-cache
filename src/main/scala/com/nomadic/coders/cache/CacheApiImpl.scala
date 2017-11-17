package com.nomadic.coders.cache
import java.time.LocalDateTime

import akka.actor.{Actor, ActorSystem, Props}
import akka.actor.Actor.Receive
import akka.routing.ConsistentHashingRouter.{ConsistentHashable, ConsistentHashableEnvelope}
import akka.routing._
import com.nomadic.coders.cache.CacheManager.Entity
import com.typesafe.config.Config
import akka.pattern.ask
import akka.util.Timeout
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.collection
import scala.collection.mutable
import scala.concurrent.Future
import scala.concurrent.duration.{Duration, FiniteDuration}
import scala.reflect.ClassTag


private[cache] class CacheApiImpl(config: Config) extends CacheApi{
  import CacheManager._

  implicit val timeout = Timeout(5 seconds)

   private val (system, cacheMgr) = {
     val sys = ActorSystem("async-cache", config)
     (sys, sys.actorOf(Props[CacheManager]))
   }

  def get[T](key: String)(implicit tag: ClassTag[T]): Future[Option[T]] = {
    (cacheMgr ? Get(key)).mapTo[Option[T]]
  }

  def getOrElse[A](key: String, expiration: Duration)(orElse: => A)(implicit tag: ClassTag[A]): Future[A] = ???

  def remove(key: String): Future[Unit] = ???

  def set(key: String, value: Any, expiration: Duration): Future[Unit] = ???
}

import CacheManager._

private[cache] class CacheManager extends Actor{
  import context._

  var stored = mutable.Set[String]()

  val router: Router = {
    Router(ConsistentHashingRoutingLogic(system)
      , Vector.fill(5)(ActorRefRoutee(context.actorOf(Props[Cache]))))
  }


  def receive: Receive = {
    case get @ Get(key) =>
      if(stored(key)) {
        router.route(ConsistentHashableEnvelope(key, get), sender())
      }else{
        sender() ! None
      }
  }
}

private [cache] class EntityWatcher extends Actor{
  import context._

   def receive: Receive = ???
}

private [cache] class Cache extends Actor{
  private var store = mutable.Map[String, Entity]()
  def receive: Receive = {
    case Get(key) =>  sender() ! store.get(key)
  }
}

object CacheManager{
  case class Entity(key: String, value: Any, timestamp: LocalDateTime, expiry: FiniteDuration)
  case class Add(key: String, value: Any)
  case class Get(Key: String)
  case class Remove(key: String)
  case class RemoveExpired(entity: Entity)
}