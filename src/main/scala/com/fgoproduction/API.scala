package com.fgoproduction

import java.io.File

import com.fasterxml.jackson.databind.ObjectMapper
import com.moandjiezana.toml.{Toml, TomlWriter}
import spark.Spark.stop
import spark.{Request, Response}

import scala.collection.JavaConverters._

object API {
  def initServer(startURL: String)(req: Request, res: Response): Response = {
    if (new CategoryPageHandler(startURL).init()) {
      res.status(200)
    } else {
      res.status(500)
    }
    res
  }

  def stopServer(req: Request, res: Response): String = {
    stop()

    ""
  }

  def changeConf(filepath: String)(req: Request, res: Response): String = {
    val params = new ObjectMapper().readTree(req.body())
    val newConf = List("port", "geckodriver_location", "dir").map {
      case k@"port" => (k, params.get(k).asInt())
      case k => (k, params.get(k).asText())
    }.toMap.asJava
    new TomlWriter().write(newConf, new File(filepath))
    ""
  }

  def getConf(conf: Toml)(req: Request, res: Response): Response = {
    res.`type`("application/json")
    res.body(new ObjectMapper().writeValueAsString(conf.toMap))
    res
  }
}
