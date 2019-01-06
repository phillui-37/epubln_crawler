package com.fgoproduction

import java.io.{BufferedReader, File, InputStreamReader}
import java.net.{URI, URL}
import java.util.concurrent.{ExecutorService, Executors}

import com.moandjiezana.toml.Toml
import spark.Spark._

import scala.collection.mutable
import scala.io.Codec
import scala.language.postfixOps
import scala.io.Source._

object Main extends App {
  def startUrl: String = "http://epubln.blogspot.com/"

  def globalPool: ExecutorService = Executors.newFixedThreadPool(Runtime.getRuntime.availableProcessors)

  override def main(args: Array[String]): Unit = {
    val conf = config
    java.awt.Desktop.getDesktop.browse(new URI(s"http://localhost:${conf.getLong("port").toString}"))
    setUp(conf)
  }

  def config: Toml = new Toml().read(new File(confFileName))

  def confFileName: String = "conf.toml"

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
      post("download", Pages.download(dir))
    })
    path(
      "/api",
      () => {
        post("/init_server", API.initServer(startUrl))
        post("/stop", API.stopServer)
        get("/download_dir", (_, _) => dir)
        post("/change_conf", API.changeConf(confFileName))
        get("/conf", API.getConf(config))
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
