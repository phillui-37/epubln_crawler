package com.fgoproduction

import java.util.NoSuchElementException

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.select.Elements

import scala.collection.JavaConverters._
import scala.io.Codec
import scala.io.Source.fromURL

sealed trait PageHandler {
  val doc: Document

  def apply(): List[PageHandler]

  def docFn(url: String): Document = Jsoup parse src(url)

  def src(url: String): String = fromURL(url)(Codec.UTF8).mkString

  def getHrefs(url: String, selector: String): List[String] = {
    selectDoc(url, selector).asScala
      .map(_ attr "href")
      .toList
  }

  def selectDoc(url: String, selector: String): Elements =
    doc select selector
}

class CategoryPageHandler(url: String) extends PageHandler {
  override lazy val doc: Document = docFn(url)

  override def apply(): List[PageHandler] = {
    getAllBooksDetailPageLinks.map(new DownloadPageHandler(_)) ++
      getPrevPageLink.map(new CategoryPageHandler(_))
  }

  def getAllBooksDetailPageLinks: List[String] = getHrefs(url, ".anes")

  def getPrevPageLink: Option[String] = {
    val target = getHrefs(url, "#Blog1_blog-pager-older-link")
    target.headOption
  }
}

class DownloadPageHandler(url: String) extends PageHandler {
  override lazy val doc: Document = docFn(url)

  override def apply(): List[PageHandler] = {
    // get title, img_link, dl_link
    try {
      println(s"$getTitle,$url,$getImgLink,${getDlLink.getOrElse("N/A")}")
    } catch {
      case e: NoSuchElementException =>
        println(s"FUCK! $url")
        throw e
      case x: Throwable => throw x
    }
    List()
  }

  def getTitle: String =
    doc
      .getElementsByAttributeValue("rel", "bookmark")
      .asScala
      .map(_.text())
      .head

  def getImgLink: String = {
    doc
      .getElementsByAttributeValue("imageanchor", "1")
      .asScala
      .map(_ attr "href")
      .filterNot(_ contains "blogger")
      .headOption match {
      case Some(link) => link
      case None =>
        doc
          .getElementsByTag("img")
          .asScala
          .map(_ attr "src")
          .filter(_ contains "bp.blogspot.com")
          .head
    }
  }

  def getDlLink: Option[String] =
    doc
      .getElementsByTag("a")
      .asScala
      .map(_ attr "href")
      .find(
        x =>
          x.contains("drive.google") || x.contains("mega") || x.contains(
            "adf.ly") || x.contains("docs.google") || x.contains("mediafire"))
}

class DownloadImageHandler(url: String) extends PageHandler {
  override lazy val doc: Document = docFn(url)

  override def apply(): List[PageHandler] = {
    List()

  }
}

class DownloadGooHandler(url: String) extends PageHandler {
  override lazy val doc: Document = docFn(url)

  override def apply(): List[PageHandler] = {
    List()

  }
}

class DownloadMegaHandler(url: String) extends PageHandler {
  override lazy val doc: Document = docFn(url)

  override def apply(): List[PageHandler] = {
    List()

  }
}

class AdflyHandler(url: String) extends PageHandler {
  override lazy val doc: Document = docFn(url)

  override def apply(): List[PageHandler] = {
    List()

  }
}
