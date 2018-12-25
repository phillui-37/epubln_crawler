package com.fgoproduction

import java.io.File
import java.util.concurrent.ExecutorService

import scala.annotation.tailrec
import scala.language.postfixOps


object Main extends App {
  def initDB(): Unit = {
    List(new Series(), new RawBookDetail(), new Book(), new Tag(), new TagBookMap())
      .foreach(_ ())
  }
  override def main(args: Array[String]): Unit = {
    initDB()
    System.setProperty("webdriver.gecko.driver", s"${System.getProperty("user.dir")}${File.separator}geckodriver.exe")
    val url = "http://epubln.blogspot.com/"
    //    val pool = java.util.concurrent.Executors.newFixedThreadPool(Runtime.getRuntime.availableProcessors)
    //    exec(pool, new CategoryPageHandler(url)())

    //    new DownloadImageHandler("https://4.bp.blogspot.com/-lo4EL8MeMRU/XBpqK29tt4I/AAAAAAAAC0k/gXvx_-jhGLkrUwEpby05z0e5_aPv4QnWACLcBGAs/s1600/01.jpg")()
    //    new DownloadGooHandler("https://drive.google.com/open?id=1dah5YX6AE62c4v88MoAq2OUkHkg2XIJa")()
    new DownloadMegaHandler("https://mega.nz/#!YEZzXSxK!FtqZCn5t-ieExGFlSU-nST9vYchS3HgAiNLolXFaZws")()
  }

  @tailrec def exec(pool: ExecutorService, it: List[PageHandler]): List[PageHandler] = {
    if (it.isEmpty) {
      pool.shutdownNow()
      return List()
    }
    exec(pool, it.map(x => pool.submit(() => x())).flatMap(_ get))
  }
}