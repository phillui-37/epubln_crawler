package com.fgoproduction

import java.sql.{DriverManager, PreparedStatement, ResultSet, Statement}

import com.fgoproduction.DownloadLinkType.DownloadLinkType

import scala.collection.mutable
import scala.language.postfixOps

object DataBase extends Enumeration {
  type DataBase = Value
  val Series, Book, Tag, TagBookMap, RawBookDetail = Value
}

object DownloadLinkType extends Enumeration {
  type DownloadLinkType = Value
  val NA, GooFile, GooFolder, Mega, Adfly, Other = Value
}

sealed trait DBHandler {
  val connStr = "jdbc:sqlite:lightnovel.db"
  Class.forName("org.sqlite.JDBC")

  type Fields
  val dbName: String
  val initDbSql: String

  def apply(): Unit = execute(_.execute(initDbSql))

  def write(): Unit

  def getRow(result: ResultSet): Fields

  def select(condition: Iterator[String]): Iterator[Fields] = {
    val sqlCache: mutable.StringBuilder =
      new mutable.StringBuilder(s"SELECT * from $dbName")
    if (condition.nonEmpty) {
      sqlCache.append(" WHERE " + condition.mkString(" AND "))
    }
    val ret: mutable.MutableList[Fields] = mutable.MutableList()
    execute(x => {
      val result = x.executeQuery(sqlCache.toString)
      while (result.next()) {
        ret += getRow(result)
      }
    })
    ret.iterator
  }

  /**
    * To execute sql
    *
    * If you want to get the result, like fetch/next, run it in the closure
    */
  protected def execute[T](f: Statement => T): T = {
    val conn = DriverManager getConnection connStr
    val stmt = conn.createStatement()
    try {
      f(stmt)
    } finally {
      stmt.close()
      conn.close()
    }
  }

  protected def prepareExecute(sql: String,
                               f: PreparedStatement => Any): Any = {
    val conn = DriverManager getConnection connStr
    val stmt = conn.prepareStatement(sql)
    try {
      f(stmt)
      stmt.executeUpdate()
    } finally {
      stmt.close()
      conn.close()
    }
  }
}

class Series(val name: String,
             val publisher: String,
             val ignored: Boolean,
             val downloadProgress: Int,
             val ended: Boolean,
             isInit: Boolean = true)
  extends DBHandler {
  override type Fields = (Int, String, String, Boolean, Int, Boolean)

  override val dbName: String = "series"
  override val initDbSql: String =
    s"""
       | CREATE TABLE IF NOT EXISTS $dbName (
       |     id INTEGER PRIMARY KEY AUTOINCREMENT,
       |     name VARCHAR(128) NOT NULL,
       |     publisher VARCHAR(128) NOT NULL,
       |     ignored BOOLEAN NOT NULL ,
       |     download_progress INTEGER NOT NULL,
       |     ended BOOLEAN NOT NULL
       | );
      """.stripMargin

  def this() = {
    this("", "", false, 0, false, false)
  }

  override def getRow(result: ResultSet): Fields = (
    result.getInt("id"),
    result.getString("name"),
    result.getString("publisher"),
    result.getBoolean("ignored"),
    result.getInt("download_progress"),
    result.getBoolean("ended")
  )

  def write(): Unit = {
    require(!name.isEmpty)
    require(!publisher.isEmpty)
    val sql =
      s"INSERT INTO $dbName(name,publisher,ignored,download_progress,ended) values(?,?,?,?,?);"
    prepareExecute(sql, stmt => {
      stmt.setString(1, name)
      stmt.setString(2, publisher)
      stmt.setBoolean(3, ignored)
      stmt.setInt(4, downloadProgress)
      stmt.setBoolean(5, ended)
    })
  }
}

class Book(val seriesId: Int,
           val rawBookDetailId: Int,
           val name: String,
           val seriesOrder: Int,
           val filePath: String,
           val coverPath: String,
           isInit: Boolean = true)
  extends DBHandler {
  override type Fields = (Int, Int, Int, String, Int, String, String)
  override val dbName: String = "book"
  override val initDbSql: String =
    s"""
       | CREATE TABLE IF NOT EXISTS $dbName (
       |     id                  INTEGER PRIMARY KEY AUTOINCREMENT,
       |     series_id           INTEGER NOT NULL,
       |     raw_book_detail_id  INTEGER NOT NULL,
       |     name                TEXT NOT NULL,
       |     series_order        INTEGER NOT NULL,
       |     file_path           TEXT NOT NULL,
       |     cover_path          TEXT NOT NULL,
       |     FOREIGN KEY(series_id) REFERENCES series(id),
       |     FOREIGN KEY(raw_book_detail_id) REFERENCES raw_book_detail(id)
       | );
        """.stripMargin

  def this() = {
    this(0, 0, "", 0, "", "", false)
  }

  override def getRow(result: ResultSet): Fields = (
    result.getInt("id"),
    result.getInt("series_id"),
    result.getInt("raw_book_detail_id"),
    result.getString("name"),
    result.getInt("series_order"),
    result.getString("file_path"),
    result.getString("cover_path")
  )

  def write(): Unit = {
    if (!isInit) throw new RuntimeException(s"$this not initialized")
    require(seriesId > 0)
    require(rawBookDetailId > 0)
    require(!name.isEmpty)
    require(!filePath.isEmpty)
    require(!coverPath.isEmpty)
    val sql =
      s"INSERT INTO $dbName(series_id,raw_book_detail_id,name,series_order,file_path,cover_path) values(?,?,?,?,?,?,?);"
    prepareExecute(
      sql,
      stmt => {
        stmt.setInt(1, seriesId)
        stmt.setInt(2, rawBookDetailId)
        stmt.setString(3, name)
        stmt.setInt(4, seriesOrder)
        stmt.setString(5, filePath)
        stmt.setString(6, coverPath)
      }
    )
  }
}

class Tag(val tag: String, isInit: Boolean = true) extends DBHandler {
  override type Fields = (Int, String)
  override val dbName: String = "tag"
  override val initDbSql: String =
    s"""
       | CREATE TABLE IF NOT EXISTS $dbName (
       |     id  INTEGER PRIMARY KEY AUTOINCREMENT,
       |     tag VARCHAR(128) NOT NULL
       | );
      """.stripMargin
  def this() = {
    this("", false)
  }

  override def getRow(result: ResultSet): Fields = (
    result.getInt("id"),
    result.getString("tag")
  )

  def write(): Unit = {
    if (!isInit) throw new RuntimeException(s"$this not initialized")
    require(!tag.isEmpty)
    val sql = s"INSERT INTO $dbName(tag) values(?);"
    prepareExecute(sql, stmt => {
      stmt.setString(1, tag)
    })
  }
}

class TagBookMap(val bookId: Int, val tagId: Int, isInit: Boolean = true)
  extends DBHandler {
  type Fields = (Int, Int, Int)
  override val dbName: String = "tag_book_map"
  override val initDbSql: String =
    s"""
       |CREATE TABLE IF NOT EXISTS $dbName (
       |    id      INTEGER PRIMARY KEY AUTOINCREMENT,
       |    book_id INTEGER NOT NULL,
       |    tag_id  INTEGER NOT NULL,
       |    FOREIGN KEY(book_id) REFERENCES book(id),
       |    FOREIGN KEY(tag_id) REFERENCES tag(id)
       |);
      """.stripMargin

  def this() = this(0, 0, false)

  override def getRow(result: ResultSet): Fields = (
    result.getInt("id"),
    result.getInt("book_id"),
    result.getInt("tag_id")
  )

  def write(): Unit = {
    if (!isInit) throw new RuntimeException(s"$this not initialized")
    require(bookId > 0)
    require(tagId > 0)
    val sql = s"INSERT INTO $dbName(book_id,tag_id) values(?,?);"
    prepareExecute(sql, stmt => {
      stmt.setInt(1, bookId)
      stmt.setInt(2, tagId)
    })
  }
}

class RawBookDetail(val name: String,
                    val pageLink: String,
                    val imgLink: String,
                    val dlLink: String,
                    val finished: Boolean,
                    val dlLinkType: DownloadLinkType,
                    isInit: Boolean = true)
  extends DBHandler {
  override type Fields =
    (Int, String, String, String, String, Boolean, DownloadLinkType)
  override val dbName: String = "raw_book_detail"
  override val initDbSql: String =
    s"""
       |CREATE TABLE IF NOT EXISTS $dbName (
       |    id              INTEGER PRIMARY KEY AUTOINCREMENT,
       |    name            VARCHAR(128) NOT NULL,
       |    page_link       TEXT NOT NULL UNIQUE,
       |    img_link        TEXT NOT NULL,
       |    dl_link         TEXT NOT NULL,
       |    finished        BOOLEAN DEFAULT 0,
       |    dl_link_type    INTEGER NOT NULL
       |);
      """.stripMargin

  def this() = this("", "", "", "", false, DownloadLinkType.NA, false)

  def isNew: Boolean =
    execute(_.executeQuery(s"select * from $dbName limit 1"))
      .next()

  def isRecordExists(dlPageLink: String): Boolean = {
    val sql =
      s"SELECT page_link from $dbName where page_link='$dlPageLink'"
    execute(x => {
      val result = x.executeQuery(sql)
      result.next()
    })
  }

  override def getRow(result: ResultSet): Fields = (
    result.getInt("id"),
    result.getString("name"),
    result.getString("page_link"),
    result.getString("img_link"),
    result.getString("dl_link"),
    result.getBoolean("finished"),
    DownloadLinkType(result.getInt("dl_link_type"))
  )

  def write(): Unit = {
    if (!isInit) throw new RuntimeException(s"$this not initialized")
    require(!name.isEmpty)
    require(!pageLink.isEmpty)
    require(!imgLink.isEmpty)
    require(!dlLink.isEmpty)
    val sql =
      s"INSERT INTO $dbName(name,page_link,img_link,dl_link,finished,dl_link_type) values(?,?,?,?,?,?);"
    prepareExecute(
      sql,
      stmt => {
        stmt.setString(1, name)
        stmt.setString(2, pageLink)
        stmt.setString(3, imgLink)
        stmt.setString(4, dlLink)
        stmt.setBoolean(5, finished)
        stmt.setInt(6, dlLinkType.id)
      }
    )
  }
}
