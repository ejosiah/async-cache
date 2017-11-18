package com.nomadic.coders.cache
import java.time.LocalDateTime

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props}
import akka.pattern._
import akka.routing.ConsistentHashingRouter.ConsistentHashable
import akka.routing._
import akka.util.Timeout
import com.nomadic.coders.cache.CacheManager.Entity
import com.typesafe.config.Config

import scala.collection.mutable
import scala.concurrent.Future
import scala.concurrent.duration.{Duration, FiniteDuration, _}
import scala.language.{implicitConversions, postfixOps}
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

  def getOrElse[A](key: String, expiration: Duration)(orElse: => A)(implicit tag: ClassTag[A]): Future[A] = {
    (cacheMgr ? GetOrElse(key, orElse, expiration)).mapTo[A]
  }

  def remove(key: String): Future[Unit] = {
    (cacheMgr ? Remove(key)).mapTo[Unit]
  }

  def set(key: String, value: Any, expiration: Duration): Future[Unit] = {
    (cacheMgr ? Add(key, value, expiration)).mapTo[Unit]
  }

  override def clear(): Future[Unit] = {
    (cacheMgr ? Clear).mapTo[Unit]
  }
}

import com.nomadic.coders.cache.CacheManager._

private[cache] class CacheManager extends Actor with ActorLogging{
  import context._
  implicit val timeout = Timeout(5 seconds)
  val stored = new Stored
  val watcher = context.actorOf(Props(classOf[EntityWatcher], self))

  val router: Router = {
    Router(ConsistentHashingRoutingLogic(system)
      , Vector.fill(5)(ActorRefRoutee(context.actorOf(Props(classOf[Cache], self, watcher)))))
  }

  def receive: Receive = {
    case get @ Get(key) if !stored(key) => sender() ! None
    case get : Get => router.route(get, sender())
    case GetOrElse(key, value, expire) if !stored(key) =>
      val f = for{
        _ <- self ? Add(key, value, expire)
        res <- (self ? Get(key)).mapTo[Option[Any]]
      } yield res.get
      f pipeTo sender()
    case GetOrElse(key, value, expire) =>  (self ? Get(key)).mapTo[Option[Any]].map(_.get) pipeTo sender()
    case add @ Add(key, value, expire) =>  router.route( add, sender())
    case remove @ Remove(key) => stored.remove(key); router.route(remove, sender())
    case Clear => stored.clear(); router.route(Broadcast(Clear), sender())
    case p: Persisted => stored.add(p); sender ! Done
    case msg @ RemoveExpired(p) if stored(p) => stored.remove(p); router.route(msg, sender())
  }
}

private [cache] class Cache(cacheMgr: ActorRef, watcher: ActorRef) extends Actor{

  import context._
  implicit val timeout = Timeout(5 seconds)
  private val store = mutable.Map[String, Entity]()

  def receive: Receive = {
    case Get(key) =>  sender() ! store.get(key).map(_.value)
    case Add(key, value, expire) =>
      val entity = Entity(value, LocalDateTime.now(), expire)
      store.put(key, entity)
      watcher ! (key, entity)
      cacheMgr ? Persisted(key, entity.timestamp) pipeTo sender()
    case Remove(key) => store.remove(key); sender() ! Done
    case RemoveExpired(Persisted(key, timestamp)) =>
      store.get(key).foreach( entity => if(entity.timestamp == timestamp) store.remove(key) )
    case Clear => store.clear(); sender() ! Done
  }
}

private [cache] class EntityWatcher(cacheMgr: ActorRef) extends Actor{
  import context._

  def receive: Receive = {
    case (key: String, Entity(_, timestamp, expiry)) if expiry != Duration.Inf =>
      // FIXME scheduler not optimal in high throughput situations, use HashedWheelTimer instead
      system.scheduler.scheduleOnce(expiry){ cacheMgr ! RemoveExpired(Persisted(key, timestamp))}
  }
}

private [cache] object CacheManager{

  sealed trait Hashable extends ConsistentHashable{
    def key: String
    def consistentHashKey = key
  }

  case class Add(key: String, value: Any, expire: Duration) extends Hashable
  case class Get(key: String) extends Hashable
  case class GetOrElse(key: String, value: Any, expire: Duration) extends Hashable
  case class Remove(key: String) extends Hashable
  case class RemoveExpired(persisted: Persisted) extends Hashable{
    override def key: String = persisted.key
  }

  case class Entity(value: Any, timestamp: LocalDateTime, expiry: Duration)
  case class Persisted(key: String, timestamp: LocalDateTime)
  case object Clear

  val Done = ()

  implicit def convert(duration: Duration): FiniteDuration = duration.asInstanceOf[FiniteDuration]
}

class Stored{
  private val map = mutable.Map[String, Persisted]()

  def add(persisted: Persisted) = map.put(persisted.key, persisted)

  def apply(key: String): Boolean = map.contains(key)

  def apply(persisted: Persisted): Boolean ={
    map.get(persisted.key).exists(_.timestamp == persisted.timestamp)
  }

  def remove(persisted: Persisted): Unit = remove(persisted.key)

  def remove(key: String): Unit = map.remove(key)

  def clear(){ map.clear() }
}