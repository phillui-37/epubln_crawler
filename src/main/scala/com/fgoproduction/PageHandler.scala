package com.fgoproduction

import org.jsoup.Jsoup
import org.jsoup.nodes.Document

import scala.io.Source.fromURL

class PageHandler(url: String) {
  private val src: String = fromURL(url).mkString

  def iter(): Iterator[String] = this.src split '\n' iterator

  def doc(): Document = Jsoup parse src
}

