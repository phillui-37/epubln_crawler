package com.fgoproduction

import java.util.concurrent.ExecutorService

import scala.annotation.tailrec
import scala.language.postfixOps


object Main extends App {
  implicit val pool: ExecutorService = java.util.concurrent.Executors.newFixedThreadPool(Runtime.getRuntime.availableProcessors)

  def initDB(): Unit = {
    List(new Series(), new RawBookDetail(), new Book(), new Tag(), new TagBookMap())
      .foreach(_ ())
  }
  override def main(args: Array[String]): Unit = {
    initDB()
    val url = "http://epubln.blogspot.com/"
    //    val t = LocalTime.now()
    //    exec(new CategoryPageHandler(url)())
    //    println(Duration.between(t, LocalTime.now()).toSeconds)

    new DownloadImageHandler("https://3.bp.blogspot.com/-Pi4U_CUuokc/XAvLN6hdqdI/AAAAAAAABOg/-ORomqm4RWw2EiHLPuGf_U65UHpj4NraACLcBGAs/s1600/16.jpg")()
    new DownloadGooHandler("https://drive.google.com/open?id=1dah5YX6AE62c4v88MoAq2OUkHkg2XIJa")()
    new DownloadGooHandler("https://drive.google.com/file/d/18gS4yWC83a6KSNVy0jdijL5bqAEopuDE/view?usp=sharing")()
  }

  @tailrec def exec(it: List[PageHandler])(implicit pool: ExecutorService): List[PageHandler] = {
    if (it.isEmpty) {
      pool.shutdownNow()
      return List()
    }
    exec(it.tail ++ it.head())
  }
}