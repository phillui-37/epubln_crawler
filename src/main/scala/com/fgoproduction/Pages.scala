package com.fgoproduction

import java.io.File

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import spark.template.velocity.VelocityTemplateEngine
import spark.{ModelAndView, Request, Response}

import scala.collection.JavaConverters._
import scala.collection.mutable
import scala.io.Source
import scala.language.postfixOps

object Pages {
  val mapper = new ObjectMapper()

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

  def rawBookDetailList(req: Request, res: Response): Response = {
    val params = mapper.readTree(req.body())
    val default = mutable.HashMap(
      "limit" -> 20,
      "offset" -> 0,
      "isDesc" -> false,
      "field" -> "name",
      "search" -> List(),
      "isAll" -> false
    )
    if (params != null) {
      default.keys.foreach(k => {
        if (params.has(k)) {
          default(k) = default(k) match {
            case _: Int => params.get(k).asInt()
            case _: Boolean => params.get(k).asBoolean()
            case _: String => params.get(k).asText()
            case _: List[Any] =>
              params.get(k).asText().split(" ").filter(_.nonEmpty).toList
          }
        }
      })
    }
    var condition = mutable.MutableList[String]()
    if (!default("isAll").asInstanceOf[Boolean]) {
      condition += "finished=false"
    }
    if (default("search").asInstanceOf[List[String]].nonEmpty) {
      default("search")
        .asInstanceOf[List[String]]
        .foreach(x => condition += s"name LIKE '%$x%'")
    }
    val result = new RawBookDetail()
      .select(
        condition.iterator,
        order = default("field").asInstanceOf[String],
        isDesc = default("isDesc").asInstanceOf[Boolean],
        limit = default("limit").asInstanceOf[Int],
        offset = default("offset").asInstanceOf[Int]
      )
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

  def totalRawRecordSize(req: Request, res: Response): Response = {
    val params = mapper.readTree(req.body())
    val default = if (params != null && params.has("related_names")) {
      try {
        params.get("related_names").asInstanceOf[List[String]]
      } catch {
        case _: java.lang.ClassCastException => List()
        case e: Throwable => throw e
      }
    } else {
      List()
    }
    val isAll = if (params != null && params.has("is_all")) {
      params.get("is_all").asBoolean(true)
    } else {
      false
    }
    val result = new RawBookDetail()
      .recordsCount(default.map(x => s"name LIKE '%$x%'").iterator ++ {
        if (!isAll) {
          Iterator(s"finished=0")
        } else Iterator()
      })
    res.body(mapper.writeValueAsString(result))
    res.`type`("application/json")
    res
  }

  def download(dir: String)(req: Request, res: Response): Response = {
    val params = mapper.readTree(req.body)
    if (params == null || !params.has("dlLinks")) {
      res.status(400)
      return res
    }
    val finalDir = params.get("dir").asText() match {
      case t if t.isEmpty => dir
      case t => t
    }
    val db = new RawBookDetail()
    var taskQueue = mutable.MutableList[PageHandler]()
    val nonHandledList = mutable.HashMap[String, String]()
    params
      .get("dlLinks")
      .asInstanceOf[ArrayNode]
      .asScala
      .map(_.asText())
      .foreach(link => {
        val record = db.select(Iterator(s"dl_link='$link'"))
        if (record.nonEmpty) {
          val target = record.next
          target._7 match {
            case DownloadLinkType.Adfly =>
              taskQueue += new AdflyHandler(target._5, finalDir)
            case DownloadLinkType.Mega =>
              taskQueue += new DownloadMegaHandler(target._5, finalDir)
            case DownloadLinkType.GooFile =>
              taskQueue += new DownloadGooHandler(target._5, finalDir)
            case DownloadLinkType.GooFolder =>
              nonHandledList.put(target._2, target._3)
            case DownloadLinkType.Other => throw new RuntimeException("Not supported")
          }
        }
      })
    while (taskQueue.nonEmpty) {
      taskQueue = taskQueue
        .flatMap(_())
        .asInstanceOf[mutable.MutableList[PageHandler]]
    }
    res.body(mapper.writeValueAsString(if (nonHandledList.nonEmpty) {
      nonHandledList.asJava
    } else {
      "Success"
    }))
    res.`type`("application/json")
    res
  }

  object Admin {
    def index(port: Int)(req: Request, res: Response): String = {
      val f = new File("static/page/admin.html")
      if (f.exists()) {
        Source.fromFile(f).getLines().mkString("\n")
      } else {
        val model = Map("port" -> port).asJava
        new VelocityTemplateEngine().render(
          new ModelAndView(model, "page/admin.vm")
        )
      }
    }


  }
}
