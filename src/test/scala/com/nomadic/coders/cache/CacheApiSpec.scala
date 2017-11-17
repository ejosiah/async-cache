package com.nomadic.coders.cache

import org.scalatest.{AsyncWordSpecLike, Matchers}

class CacheApiSpec extends AsyncWordSpecLike with Matchers{

  val cache = CacheApi()

  "Cache" should{
    "return nothing when item not in cache" in {
      cache.get[String]("unknown").map(_ shouldBe None)
    }
  }

}
