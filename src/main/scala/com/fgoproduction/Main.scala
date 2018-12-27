package com.fgoproduction

import java.io.File
import java.util

import com.moandjiezana.toml.Toml
import spark.ModelAndView
import spark.Spark._
import spark.template.velocity.VelocityTemplateEngine

import scala.language.postfixOps

object Main extends App {
  final val startUrl = "http://epubln.blogspot.com/"

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
    setUpPath(p)
  }

  def setUpPath(port: Int): Unit = {
    get("/", (_, _) => {
      val model = new util.HashMap[String, Any]()
      model.put("port", port)
      new VelocityTemplateEngine().render(
        new ModelAndView(model, "template/index.vm")
      )
    })
    path("/api", () => {
      post("/init_server", (_, res) => {
        if (new CategoryPageHandler(startUrl).init()) {
          res.status(200)
          "Success"
        } else {
          res.status(500)
          "Fail"
        }
      })
      post("/stop", (_, _) => {
        stop()
        ""
      })
    })
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
