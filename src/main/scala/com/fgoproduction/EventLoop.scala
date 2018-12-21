package com.fgoproduction

import java.util

import com.fgoproduction.Action._

class EventLoop {
  val tasks: util.LinkedList[(Action, Any)] = new util.LinkedList()
  def submit[T](action: Action, param: Any): Unit = {
    tasks.addLast((action, param))
  }
  def execute(): Unit = {
    while (!tasks.isEmpty) {
      val (act, param) = tasks.removeFirst()
      act.outerEnum match {
        case CategoryPage => ""
        case DownloadGoo => ""
        case UnshortenURL => ""
        case UnknownDownload => ""
        case DownloadPage => ""
        case DownloadTitle => ""
        case DownloadMEGA => ""
        case DownloadImage => ""
      }
    }
  }
}
