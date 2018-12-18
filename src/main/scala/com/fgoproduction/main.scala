package com.fgoproduction

import java.util

import org.jsoup.nodes.Element

import scala.collection.JavaConversions._
import scala.collection.mutable

object main extends App {
  override def main(args: Array[String]): Unit = {
    val url = "http://epubln.blogspot.com/"
    val startPage = new PageHandler(url)
    val hrefs = startPage
        .doc()
        .select(".anes")
        .toList
        .map(_ attr "href")

  }
}
