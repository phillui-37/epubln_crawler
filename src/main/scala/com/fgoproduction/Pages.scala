package com.fgoproduction

import java.io.File

import com.fasterxml.jackson.databind.ObjectMapper
import spark.template.velocity.VelocityTemplateEngine
import spark.{ModelAndView, Request, Response}

import scala.collection.JavaConverters._
import scala.collection.mutable
import scala.io.Source

object Pages {
  def index(port: Int)(req: Request, res: Response): String = {
    val f = new File("static/page/index.html")
    if (f.exists()) {
      Source.fromFile(f).getLines().mkString("\n")
    } else {
      val model = Map("port" -> port).asJava
      new VelocityTemplateEngine().render(
        new ModelAndView(model, "page/index.vm")
      )
    }
  }

  def unfinishedRawBookDetailList(req: Request, res: Response): Response = {
    val mapper = new ObjectMapper()
    val params = mapper.readTree(req.body())
    val default = mutable.HashMap(
      "limit" -> 20, "offset" -> 0, "isDesc" -> false, "field" -> "name"
    )
    params.has("limit")
    default.keys.foreach(k => {
      if (params.has(k)) {
        default(k) = default(k) match {
          case _: Int => params.get(k).asInt()
          case _: Boolean => params.get(k).asBoolean()
          case _: String => params.get(k).asText()
        }
      }
    })
    val result = new RawBookDetail()
      .select(Iterator("finished=false"),
        order = default("field").asInstanceOf[String],
        isDesc = default("isDesc").asInstanceOf[Boolean],
        limit = default("limit").asInstanceOf[Int],
        offset = default("offset").asInstanceOf[Int])
      .map(
        x =>
          Map(
            "name" -> x._2,
            "page_link" -> x._3,
            "img_link" -> x._4,
            "dl_link" -> x._5
          ).asJava
      )
      .toList
      .asJava
    val json = mapper.writeValueAsString(result)
    res.body(json)
    res.`type`("application/json")
    res
  }
}
