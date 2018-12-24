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

  def downloadImage(url: String, path: String): Unit = {
    val link = new URL(url)
    val conn = link.openConnection().asInstanceOf[HttpsURLConnection]
    val fieldValue = conn.getHeaderField("Content-Disposition")

    val fileName = URLDecoder.decode(if (fieldValue.contains("filename*=UTF-8''")) {
      fieldValue.substring(fieldValue.indexOf("filename*=UTF-8''") + 17, fieldValue.length())
    } else {
      fieldValue.substring(fieldValue.indexOf("filename=\"") + 10, fieldValue.length() - 1)
    }, "UTF-8")
    val ch = Channels.newChannel(link.openStream())
    val fos = new FileOutputStream(s"$path${File.separator}$fileName")
    fos.getChannel.transferFrom(ch, 0, Long.MaxValue)
    fos.close()
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
    // TODO get path from db, then save to path
    val path = System.getProperty("user.dir")
    downloadImage(url, path)
    List()
  }
}

class DownloadGooHandler(url: String) extends PageHandler {
  override lazy val doc: Document = docFn(url)

  val dlLink: String = {
    val prefix = if (url.contains("drive.google.com")) {
      "drive"
    } else {
      "docs"
    }
    val dlPrefix: String = s"https://$prefix.google.com/uc?export=download&id="
    List("open?id=", "file/d/")
      .map(x => s"https://$prefix.google.com/$x")
      .filter(url startsWith)
      .map(x => s"$dlPrefix${url.replace(x, "").split('/').head}")
      .head
  }

  override def apply(): List[PageHandler] = {
    // TODO
    downloadImage(dlLink, System.getProperty("user.dir"))
    List()
  }
}

class DownloadMegaHandler(url: String) extends PageHandler {
  override lazy val doc: Document = docFn(url)
  val dlPrefix = "https://drive.google.com/uc?export=download&id="

  //  def convertDownloadLink(link: String): String = {
  //    val drivePrefix = """(\w+?).google.com""".r.findAllIn(link).matchData.next()
  //  }

  override def apply(): List[PageHandler] = {
    // TODO selenium
    List()
  }
}

class AdflyHandler(url: String) extends PageHandler {
  override lazy val doc: Document = docFn(url)

  override def apply(): List[PageHandler] = {
    List()
  }
}
