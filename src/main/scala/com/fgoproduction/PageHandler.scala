package com.fgoproduction

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.select.Elements

import scala.io.Source.fromURL
import scala.collection.JavaConversions._


sealed class PageHandler(url: String) {
  protected val src: String = fromURL(url).mkString

  def iter(): Iterator[String] = src split '\n' iterator
  def doc(): Document = Jsoup parse src
  def selectDoc(selector: String): Elements = doc select selector
  def getHrefs(selector: String): List[String] = selectDoc(selector).toList.map(_ attr "href")
}

class CategoryPageHandler(url: String) extends PageHandler(url) {
  
}

