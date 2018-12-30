package com.fgoproduction

import java.io.{File, FileOutputStream}
import java.net.{URL, URLDecoder}
import java.nio.channels.Channels
import java.util.NoSuchElementException
import java.util.concurrent.ExecutorService

import javax.net.ssl.HttpsURLConnection
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.select.Elements
import org.openqa.selenium._
import org.openqa.selenium.firefox.{FirefoxDriver, FirefoxOptions, FirefoxProfile}
import org.openqa.selenium.support.ui.{ExpectedConditions, WebDriverWait}

import scala.annotation.tailrec
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

  def init(pool: ExecutorService = java.util.concurrent.Executors
    .newFixedThreadPool(Runtime.getRuntime.availableProcessors)): Boolean = {
    try {
      println("Start Init Server")
      new Thread(() => exec(pool, apply())).start()
      true
    } catch {
      case e: Exception =>
        println(e)
        false
    }
  }

  def refresh(): Unit = {
    println("Start Refresh Resource List")

    @tailrec def core(ls: List[PageHandler]): List[PageHandler] = {
      ls.head match {
        case handler: DownloadPageHandler =>
          if (!handler.isNew) {
            return List()
          }
        case _ =>
      }
      core(ls.tail ++ ls.head())
    }

    core(apply())
    println("Finished")
  }

  @tailrec private def exec(pool: ExecutorService,
                            ls: List[PageHandler]): List[PageHandler] = {
    if (ls.isEmpty) {
      println("Finish Init Server")
      pool.shutdown()
      return List()
    }
    exec(pool, ls.map(x => pool.submit(() => x())).flatMap(_ get))
  }
}

class DownloadPageHandler(url: String) extends PageHandler {
  override lazy val doc: Document = docFn(url)

  def isNew: Boolean = new RawBookDetail().isNew

  private def isFolder(link: String): Boolean =
    link.contains("google") && (link.contains("drive/folders") || link
      .contains("folderview"))

  override def apply(): List[PageHandler] = {
    if (new RawBookDetail().isRecordExists(url)) {
      println(s"Resource detail at $url is already fetched")
      return List()
    }

    try {
      val title = getTitle
      println(s"Handling $title")
      val imgLink = getImgLink
      val dlLink = getDlLink.getOrElse("N/A")
      val dlLinkType = Map(
        "N/A" -> DownloadLinkType.NA,
        "google" -> {
          if (isFolder(dlLink)) {
            DownloadLinkType.GooFolder
          } else {
            DownloadLinkType.GooFile
          }
        },
        "mega" -> DownloadLinkType.Mega,
        "adf.ly" -> DownloadLinkType.Adfly
      ).filterKeys(dlLink contains).headOption match {
        case Some((_, v)) => v
        case None => DownloadLinkType.Other
      }
      new RawBookDetail(title, url, imgLink, dlLink, false, dlLinkType).write()
    } catch {
      case e: NoSuchElementException =>
        println(s"FUCK! $url")
        throw e
      case x: Throwable => throw x
    }
    List()
  }

  private def getTitle: String =
    doc
      .getElementsByAttributeValue("rel", "bookmark")
      .asScala
      .map(_.text())
      .head

  private def getImgLink: String = {
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

  private def getDlLink: Option[String] =
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

class DownloadImageHandler(url: String, downloadDir: String) extends PageHandler {
  override lazy val doc: Document = docFn(url)

  override def apply(): List[PageHandler] = {
    download(url, downloadDir)
    List()
  }
}

class DownloadGooHandler(url: String, downloadDir: String) extends PageHandler {
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
    val ins = new RawBookDetail()
    try {
      download(getGooDownloadLink, downloadDir)
    } catch {
      case _: NullPointerException =>
        val link = ins.select(Iterator(s"dl_link='$url'")).next()._3
        println(s"Page resource $link is not valid now, please verify")
      case e: Exception => throw e
    }
    ins.finish(ins.select(Iterator(s"dl_link='$url'")).next._1)
    List()
  }
}

class DownloadMegaHandler(url: String, downloadDir: String)
  extends PageHandler {
  override lazy val doc: Document = docFn(url)

  private def setUpProfile: FirefoxProfile = {
    val profile = new FirefoxProfile()
    profile.setPreference("browser.download.panel.shown", false)
    profile.setPreference("browser.download.manager.showWhenStarting", false)
    profile.setPreference("browser.download.folderList", 2)
    profile.setPreference("browser.download.dir", downloadDir)
    profile.setPreference("browser.helperApps.neverAsk.saveToDisk",
      "application/epub+zip")
    profile
  }

  private def setupOptions(profile: FirefoxProfile): FirefoxOptions = {
    val options = new FirefoxOptions()
    options.setHeadless(true)
    options.setProfile(profile)
  }

  private def waitForPageReadyAndClick(browser: FirefoxDriver, waitTimeout: Long = 20): Unit = {
    val clickBtn = new WebDriverWait(browser, waitTimeout)
      .until(
        ExpectedConditions.presenceOfElementLocated(
          By.xpath(
            "//div[@class='download big-button download-file red transition']")
        )
      )
    clickBtn.click()
  }

  private def downloadCore(browser: FirefoxDriver): Unit = {
    while (true) {
      try {
        val dlProgress = browser
          .findElementByXPath("//div[@class='download info-txt small-txt']")
          .findElements(By.tagName("span"))
        if (dlProgress.asScala
          .map(_.getText)
          .toSet
          .size == 1 && dlProgress.size() != 1) {
          throw new StaleElementReferenceException("Finish")
        }
      } catch {
        case _: NoSuchElementException | _: InvalidSelectorException =>
        case e: Exception => throw e
      } finally {
        Thread.sleep(1000)
      }
    }
  }

  private def startDownload(browser: FirefoxDriver): Unit = {
    browser.get(url)
    try {
      waitForPageReadyAndClick(browser)
      downloadCore(browser)
    } catch {
      case _: StaleElementReferenceException =>
      case e: Exception => throw e
    } finally {
      browser.quit()
    }
  }

  override def apply(): List[PageHandler] = {
    val browser = new FirefoxDriver(setupOptions(setUpProfile))
    startDownload(browser)
    val ins = new RawBookDetail()
    ins.finish(ins.select(Iterator(s"dl_link='$url'")).next._1)
    List()
  }
}

class AdflyHandler(url: String, downloadDir: String) extends PageHandler {
  override lazy val doc: Document = docFn(url)

  override def apply(): List[PageHandler] = {
    val link = doc
      .getElementsByTag("iframe")
      .asScala
      .filter(_ hasAttr "allowtransparency")
      .head
      .attr("src")
    List(new DownloadGooHandler(link, downloadDir))
  }
}
