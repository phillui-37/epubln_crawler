package com.fgoproduction

import java.util.concurrent.ExecutorService

import scala.annotation.tailrec
import scala.language.postfixOps


object Main extends App {
  def initDB(): Unit = {
    List(new Series(), new RawBookDetail(), new Book(), new Tag(), new TagBookMap())
      .foreach(_ ())
  }
  override def main(args: Array[String]): Unit = {
    initDB()
    val url = "http://epubln.blogspot.com/"
    val pool = java.util.concurrent.Executors.newFixedThreadPool(Runtime.getRuntime.availableProcessors)
    exec(pool, new CategoryPageHandler(url)())
    //    println(new DownloadPageHandler("http://epubln.blogspot.com/2018/11/06_7.html").getImgLink)
  }

  @tailrec def exec(pool: ExecutorService, it: List[PageHandler]): List[PageHandler] = {
    if (it.isEmpty) {
      pool.shutdownNow()
      return List()
    }
    exec(pool, it.map(x => pool.submit(() => x())).flatMap(_ get))
  }
}