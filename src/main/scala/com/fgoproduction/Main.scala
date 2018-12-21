package com.fgoproduction


object Main extends App {
  override def main(args: Array[String]): Unit = {
    val url = "http://epubln.blogspot.com/"
    val startPage = new CategoryPageHandler(url)
    startPage.getAllBooksDetailPageLinks().foreach(println)
    startPage.getPrevPageLink().foreach(println)
  }
}
