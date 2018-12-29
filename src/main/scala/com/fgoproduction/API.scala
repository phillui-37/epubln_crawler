package com.fgoproduction

import spark.Spark.stop
import spark.{Request, Response}

object API {
  def init_server(startURL: String)(req: Request, res: Response): Response = {
    if (new CategoryPageHandler(startURL).init()) {
      res.status(200)
    } else {
      res.status(500)
    }
    res
  }

  def stop_server(req: Request, res: Response): String = {
    stop()
    ""
  }
}
