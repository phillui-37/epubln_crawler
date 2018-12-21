package com.fgoproduction

object Action extends Enumeration {
  type Action = Value
  val DownloadGoo, DownloadPage, DownloadImage, DownloadMEGA, CategoryPage, DownloadTitle, UnshortenURL, UnknownDownload = Action
}
