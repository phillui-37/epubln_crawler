package com.fgoproduction

import java.io.{BufferedReader, File, FileOutputStream, InputStreamReader}
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

import scala.collection.JavaConverters._
import scala.collection.mutable
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
    new File(path).mkdirs()
    val fos = new FileOutputStream(s"$path${File.separator}$fileName")
    try {
      fos.getChannel.transferFrom(rbc, 0, Long.MaxValue)
    } finally {
      fos.close()
    }
  }
}

class CategoryPageHandler(url: String) extends PageHandler {
  override lazy val doc: Document = {
    val options = new FirefoxOptions()
    options.setHeadless(true)
    val browser = new FirefoxDriver(options)
    try {
      browser.get(url)
      val xpath = "//div[@class='widget-content list-label-widget-content']"
      new WebDriverWait(browser, 2)
        .until(ExpectedConditions.presenceOfElementLocated(By.xpath(xpath)))
      val target = browser.findElementByXPath(xpath).getAttribute("innerHTML")
      Jsoup parse target
    } finally {
      browser close()
    }
  }
  lazy val pageList: List[String] = doc
    .getElementsByTag("a")
    .asScala
    .filter(_ hasAttr "dir")
    .map(_ attr "href")
    .filter(!_.contains("%E5%85%B6%E4%BB%96"))
    .toList

  def getAllUnfinishedPage: List[String] =
    pageList
      .filter(_ contains "%E5%BE%85%E7%BA%8C")

  def getAllFinishedPage: List[String] =
    pageList
      .filter(_ contains "%E5%AE%8C%E7%B5%90")

  def init(pool: ExecutorService): Unit = {
    println("Start Init Server")
    new Thread {
      override def run(): Unit = {
        apply().map(x => pool.submit(() => x())).flatMap(_ get).map(x => pool.submit(() => x())).flatMap(_ get)
        new CrawLog(new RawBookDetail().latestRecordId).write()
      }
    }.start()
  }

  def refresh(): Unit = {
    println("Start Refresh Resource List")
    apply().foreach(_ refresh())
    println("Finished")
  }

  override def apply(): List[TagPageHandler] = {
    pageList.map(new TagPageHandler(_))
  }
}

class TagPageHandler(url: String) extends PageHandler {
  override lazy val doc: Document = docFn(url)

  override def apply(): List[DownloadPageHandler] = {
    println(s"Handling ${URLDecoder.decode(url, "UTF-8")}")
    getHrefs(url, ".anes").map(new DownloadPageHandler(_))
  }

  def refresh(): Unit = {}
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

class DownloadImageHandler(url: String, downloadDir: String)
  extends PageHandler {
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

  private def waitForPageReadyAndClick(browser: FirefoxDriver,
                                       waitTimeout: Long = 20): Unit = {
    val clickBtn = new WebDriverWait(browser, waitTimeout)
      .until(
        ExpectedConditions.presenceOfElementLocated(
          By.xpath(
            "//div[@class='download big-button download-file red transition']")
        )
      )
    Thread.sleep(1000)
    try {
      clickBtn.click()
    } catch {
      case _: ElementClickInterceptedException =>
        browser.executeScript(
          "document.querySelector(\"div.download.big-button.download-file.red.transition\").click()")
      case e: Throwable => throw e
    }
  }

  private def downloadCore(browser: FirefoxDriver): Unit = {
    Thread.sleep(1500)
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
    Thread.sleep(1500)
  }

  private def startDownload(browser: FirefoxDriver): Unit = {
    browser.get(url.replace(".co", ""))
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
  override lazy val doc: Document = {
    val ua = "Mozilla/5.0 (X11; Linux x86_64…) Gecko/20100101 Firefox/64.0"
    val urlIns = new URL(url)
    val conn = urlIns.openConnection()
    conn.setRequestProperty("User-Agent", ua)
    val br = new BufferedReader(new InputStreamReader(conn.getInputStream))
    val result = mutable.MutableList[String]()
    try {
      while (true) {
        br.readLine() match {
          case null => throw new RuntimeException()
          case tmp => result += tmp
        }
      }
    } catch {
      case _: RuntimeException =>
      case e: Exception => throw e
    } finally {
      br.close()
    }
    Jsoup parse result.reduceLeft(_ + _)
  }

  override def apply(): List[PageHandler] = {
    val link = doc
      .getElementsByTag("iframe")
      .asScala
      .filter(_ hasAttr "allowtransparency")
      .head
      .attr("src")
    new RawBookDetail().updateDownloadLink(url, link)
    List(new DownloadGooHandler(link, downloadDir))
  }
}
