package com.fgoproduction

import scala.collection.JavaConversions._

object main extends App {
  override def main(args: Array[String]): Unit = {
    val url = "http://epubln.blogspot.com/"
    val startPage = new PageHandler(url)
    startPage.getHrefs(".anes").foreach(println)

  }
}
