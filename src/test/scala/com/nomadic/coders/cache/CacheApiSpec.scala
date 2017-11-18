package com.nomadic.coders.cache

import org.scalatest.{AsyncWordSpecLike, Matchers}

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.language.postfixOps

class CacheApiSpec extends AsyncWordSpecLike with Matchers{


  "Cache" should{
    "return nothing when item not in cache" in {
      val cache = CacheApi()
      cache.get[String]("unknown").map(_ shouldBe None)
    }

    "return item present in cache" in {
      val cache = CacheApi()
      cache.set("test-key", "test-value", 1.seconds).flatMap{ _ =>
        cache.get("test-key")
      }.map{ _ shouldBe Some("test-value")}
    }

    "item should be removed from cache when it expires" in {
      val cache = CacheApi()
      cache.set("test-key", "test-value", 1 second).flatMap{ _ =>
        Thread.sleep(1.5.seconds.toMillis)
        cache.get("test-key")
      }.map{ _ shouldBe None}
    }

    "item with extended expiry should be retrievable" in {
      val cache = CacheApi()
      for{
        _ <- cache.set("test-key", "test-value", 1 second)
        _ <- cache.remove("test-key")
        _ <- cache.set("test-key", "test-value", 10 second)
        _ = Thread.sleep(1.5.seconds.toMillis)
        res <- cache.get[String]("test-key")
      } yield res shouldBe Some("test-value")
    }

    "remove all items from cache" in {
      for{
        cache <- Future.successful( CacheApi() )
        _     <- Future.sequence((1 until 10).map(i => cache.set(i.toString, i)))
        _     <- cache.clear()
        res   <- Future.sequence((1 until 10).map(i => cache.get(i.toString)))
      } yield res.forall(_.isEmpty) shouldBe true
    }

    "return orElse when no item present for key" in {
      for{
        cache <- Future.successful( CacheApi() )
        res <- cache.getOrElse[String]("test-key", 1 second)("default-value")
      } yield res shouldBe "default-value"
    }

    "return item present in cache for key" in {
      for{
        cache <- Future.successful( CacheApi() )
        _ <- cache.set("test-key", "test-value", 1 second)
        res <- cache.getOrElse[String]("test-key", 1 second)("default-value")
      } yield res shouldBe "test-value"
    }

    "return orElse when item in cache removed" in {
      for{
        cache <- Future.successful( CacheApi() )
        _ <- cache.set("test-key", "test-value", 1 second)
        _ <- cache.remove("test-key")
        res <- cache.getOrElse[String]("test-key", 1 second)("default-value")
      } yield res shouldBe "default-value"
    }

    "return orElse when item in cache expired" in {
      for{
        cache <- Future.successful( CacheApi() )
        _ <- cache.set("test-key", "test-value", 1 second)
        _ = Thread.sleep(1.5.seconds.toMillis)
        res <- cache.getOrElse[String]("test-key", 1 second)("default-value")
      } yield res shouldBe "default-value"
    }
  }

}
