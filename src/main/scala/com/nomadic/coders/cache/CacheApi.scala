package com.nomadic.coders.cache

import com.typesafe.config.{Config, ConfigFactory}

import scala.concurrent.Future
import scala.concurrent.duration.Duration
import scala.reflect.ClassTag


trait CacheApi {

  def get[T](key: String)(implicit tag: ClassTag[T]): Future[Option[T]]

  def getOrElse[A](key: String, expiration: Duration = Duration.Inf)(orElse: ⇒ A)(implicit arg0: ClassTag[A]): Future[A]

  def remove(key: String): Future[Unit]

  def set (key: String, value: Any, expiration: Duration = Duration.Inf): Future[Unit]

  def clear(): Future[Unit]

}

object CacheApi {
  def apply() = new CacheApiImpl(ConfigFactory.load())

  def apply(config: Config) = new CacheApiImpl(config)
}