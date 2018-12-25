package com.fgoproduction

import java.io.File

import spark.Spark._

import scala.language.postfixOps

object Main extends App {
  final val startUrl = "http://epubln.blogspot.com/"

  def setUp(customPort: Int): Unit = {
    initDB()
    System.setProperty(
      "webdriver.gecko.driver",
      s"${System.getProperty("user.dir")}${File.separator}geckodriver.exe"
    )
    commonSparkSetUp(customPort)
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
    notFound("<html><body><h1>404 Not Found</h1></body></html>")
    internalServerError(
      "<html><body><h1>500 Internal Server Error</h1></body></html>")
    staticFiles.location("/static")
    staticFiles.expireTime(600)
    port(customPort)
    threadPool(4)
    after((_, response) => response.header("Content-Encoding", "gzip"))
  }

  override def main(args: Array[String]): Unit = {
    //    val p = if (args.isEmpty) { 8080 } else {
    //      try {
    //        args.head.asInstanceOf[Int]
    //      } catch {
    //        case e: Exception =>
    //          println(s"${args.head} is not a valid port number.")
    //          throw e
    //      }
    //    }
    //    setUp(p)
    //    get("/", (_, _) => "Fuck you")
    //    path("/api", () => {
    //      post("/init_server", (_, _) => {
    //        if (new CategoryPageHandler(startUrl).init()) {
    //          "Success"
    //        } else {
    //          "Fail"
    //        }
    //      })
    //      post("/stop", (_, _) => {
    //        stop()
    //        "Stopped"
    //      })
    //    })
    new DownloadGooHandler("https://drive.google.com/file/d/0B_u3N_VNVprha3NqOEc3dUxNRkU/view?usp=sharing")()
  }

}
