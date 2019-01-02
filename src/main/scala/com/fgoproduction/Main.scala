package com.fgoproduction

import java.io.File
import java.util.concurrent.{ExecutorService, Executors}

import com.moandjiezana.toml.Toml
import spark.Spark._

import scala.language.postfixOps

object Main extends App {
  def startUrl: String = "http://epubln.blogspot.com/"

  def globalPool: ExecutorService = Executors.newFixedThreadPool(Runtime.getRuntime.availableProcessors)

  override def main(args: Array[String]): Unit = {
    setUp(new Toml().read(new File("conf.toml")))
  }

  def setUp(conf: Toml): Unit = {
    val p = conf.getLong("port", 8080L).toInt
    initDB()
    System.setProperty(
      "webdriver.gecko.driver",
      s"${conf.getString("geckodriver_location")}"
    )
    commonSparkSetUp(p)
    setUpPath(p, conf.getString("dir"))
  }

  def setUpPath(port: Int, dir: String): Unit = {
    path("/", () => {
      get("", Pages.index(port))
      post("raw", Pages.rawBookDetailList)
      post("count", Pages.totalRawRecordSize)
      post("download", Pages.download(dir, globalPool))
    })
    path(
      "/api",
      () => {
        post("/init_server", API.init_server(startUrl))
        post("/stop", API.stop_server)
        get("/download_dir", (_, _) => dir)
      }
    )
  }

  def initDB(): Unit = {
    List(new Series(),
      new RawBookDetail(),
      new Book(),
      new Tag(),
      new TagBookMap())
      .foreach(_ ())
  }

  def commonSparkSetUp(customPort: Int): Unit = {
    notFound("<template><body><h1>404 Not Found</h1></body></template>")
    internalServerError(
      "<template><body><h1>500 Internal Server Error</h1></body></template>")
    staticFiles.location("/")
    staticFiles.expireTime(600)
    port(customPort)
    threadPool(4)
    after((_, response) => response.header("Content-Encoding", "gzip"))
    init()
  }

}
