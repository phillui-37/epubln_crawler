package com.fgoproduction

import java.io.{File, FileOutputStream}
import java.net.{URL, URLDecoder}
import java.nio.channels.Channels
import java.util.NoSuchElementException

import javax.net.ssl.HttpsURLConnection
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.select.Elements

import scala.collection.JavaConverters._
import scala.io.Codec
import scala.io.Source.fromURL
import scala.language.postfixOps

sealed trait PageHandler {
  val doc: Document

  def apply(): List[PageHandler]

  final def docFn(url: String): Document = Jsoup parse src(url)

  final def src(url: String): String = fromURL(url)(Codec.UTF8).mkString

  final def getHrefs(url: String, selector: String): List[String] = {
    selectDoc(url, selector).asScala
      .map(_ attr "href")
      .toList
  }

  final def selectDoc(url: String, selector: String): Elements =
    doc select selector

  final private def getFileNameFromURL(url: String): String = {
    val content = new URL(url)
      .openConnection()
      .asInstanceOf[HttpsURLConnection]
      .getHeaderField("Content-Disposition")
    URLDecoder.decode(
      if (content.contains("filename*=utf-8''") ||
        content.contains("filename*=UTF-8''")) {
        content.substring(content.indexOf("''") + 2)
      } else {
        content.replaceFirst("(?i)^.*filename=\"?([^\"]+)\"?.*$", "$1")
      },
      "UTF-8"
    )
  }

  final def download(url: String, path: String): Unit = {
    val fileName = getFileNameFromURL(url)
    val urlInstance = new URL(url)
    val rbc = Channels.newChannel(urlInstance.openStream())
    val fos = new FileOutputStream(s"$path${File.separator}$fileName")
    try {
      fos.getChannel.transferFrom(rbc, 0, Long.MaxValue)
    } finally {
      fos.close()
    }
  }
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
          x.contains("drive.google") ||
            x.contains("mega") ||
            x.contains("adf.ly") ||
            x.contains("docs.google") ||
            x.contains("mediafire")
      )
}

class DownloadImageHandler(url: String) extends PageHandler {
  override lazy val doc: Document = docFn(url)

  override def apply(): List[PageHandler] = {
    // TODO
    download(url, System.getProperty("user.dir"))
    List()
  }
}

class DownloadGooHandler(url: String) extends PageHandler {
  override lazy val doc: Document = docFn(url)
  lazy private val prefix =
    """\w+?://(\w+?)\.google.com/.*$""".r
      .findAllIn(url)
      .matchData
      .next()
      .group(1)
  lazy private val downloadPrefix =
    s"https://$prefix.google.com/uc?export=download&id="

  lazy private val getGooDownloadLink: String = {
    if (url.startsWith(downloadPrefix)) {
      url
    } else {
      List("open?id=", "file/d/")
        .map(x => s"https://$prefix.google.com/$x")
        .filter(url startsWith)
        .map(x => downloadPrefix + url.replace(x, "").split('/').head)
        .head
    }
  }

  override def apply(): List[PageHandler] = {
    // TODO
    download(getGooDownloadLink, System.getProperty("user.dir"))
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
