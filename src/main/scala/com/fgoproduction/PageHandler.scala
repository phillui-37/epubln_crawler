package com.fgoproduction

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.select.Elements

import scala.io.Source.fromURL
import scala.collection.JavaConverters._


sealed class PageHandler(url: String) {
  val src: String = fromURL(url).mkString

  def iter(): Iterator[String] = src split '\n' iterator

  def doc(): Document = Jsoup parse src

  def selectDoc(selector: String): Elements = doc select selector

  def getHrefs(selector: String): Iterator[String] = {
    selectDoc(selector)
      .asScala
      .map(_ attr "href")
      .iterator
  }
}

class CategoryPageHandler(url: String) extends PageHandler(url) {
  def getAllBooksDetailPageLinks(): Iterator[String] = getHrefs(".anes")

  def getPrevPageLink(): Option[String] = {
    val target = getHrefs("#Blog1_blog-pager-older-link")
    if (!target.hasNext) {
      None
    } else {
      Some(target.next())
    }
  }
}
