package com.fgoproduction

import java.sql._

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
  val defaultValue: Map[String, Any] = Map(
    "limit" -> 20,
    "offset" -> 0
  )

  type Fields
  val dbName: String
  val initDbSql: String

  def apply(): Unit = execute(_.execute(initDbSql))

  def write(): Unit

  def getRow(result: ResultSet): Fields

  def recordsCount(condition: Iterator[String]): Int = {
    val sqlCache = new mutable.StringBuilder(s"SELECT count(1) FROM $dbName")
    if (condition.nonEmpty) {
      sqlCache.append(s" WHERE ${condition.mkString(" AND ")}")
    }
    execute(_.executeQuery(sqlCache.mkString).getInt(1))
  }

  def select(condition: Iterator[String] = Iterator(),
             order: String = "",
             isDesc: Boolean = false,
             limit: Int = defaultValue("limit").asInstanceOf[Int],
             offset: Int = defaultValue("offset").asInstanceOf[Int])
  : Iterator[Fields] = {
    val sqlCache: mutable.StringBuilder =
      new mutable.StringBuilder(s"SELECT * FROM $dbName")
    if (condition.nonEmpty) {
      sqlCache.append(" WHERE " + condition.mkString(" AND "))
    }
    if (order.nonEmpty) {
      sqlCache.append(s" ORDER BY $order ${if (isDesc) "desc" else "asc"}")
    }
    sqlCache.append(s" LIMIT $limit OFFSET $offset")
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
      synchronized(f(stmt))
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
      synchronized {
        f(stmt)
        stmt.executeUpdate()
      }
    } finally {
      stmt.close()
      conn.close()
    }
  }
}

trait ToSQL {
  def toSQLNew: String

  def toSQLAll: String
}

class Series(val name: String,
             val publisher: String,
             val ignored: Boolean,
             val downloadProgress: Int,
             val ended: Boolean,
             isInit: Boolean = true)
  extends DBHandler
    with ToSQL {
  override type Fields = (Int, String, String, Boolean, Int, Boolean)

  override val dbName: String = "series"
  override val initDbSql: String =
    s"""
       | CREATE TABLE IF NOT EXISTS $dbName (
       |     id INTEGER PRIMARY KEY AUTOINCREMENT,
       |     name VARCHAR(128) NOT NULL,
       |     publisher VARCHAR(128) NOT NULL,
       |     ignored BOOLEAN NOT NULL ,
       |     progress INTEGER NOT NULL,
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

  override def toSQLNew: String =
    execute(x => {
      val ret = mutable.MutableList[String]()
      val rs = x.executeQuery(
        s"""
           |SELECT d.name, d.publisher, d.ignored, d.progress, d.ended
           |FROM $dbName d, ${new Book().dbName} b, ${new CrawLog().dbName} c, ${new RawBookDetail().dbName} r
           |WHERE d.id=b.series_id AND b.raw_book_detail_id=r.id AND r.id > (SELECT raw_id FROM  ${new CrawLog().dbName} ORDER BY id DESC LIMIT 1 OFFSET 1)
           |ORDER BY d.id ASC;
        """.stripMargin)
      while (rs.next()) {
        ret +=
          s"""
             |INSERT INTO $dbName(name,publisher,ignored,progress,ended)
             |VALUES(${rs.getString(1)},${rs.getString(2)},${
            rs
              .getBoolean(3)
          },${rs.getInt(4)},${rs.getBoolean(5)});
            """.stripMargin
      }
      ret.mkString("\n")
    })

  override def toSQLAll: String =
    execute(x => {
      val ret = mutable.MutableList[String]()
      val rs = x.executeQuery(
        s"""
           |SELECT d.name, d.publisher, d.ignored, d.progress, d.ended
           |FROM $dbName d ORDER BY d.id ASC;
        """.stripMargin)
      while (rs.next()) {
        ret +=
          s"""
             |INSERT INTO $dbName(name,publisher,ignored,progress,ended)
             |VALUES(${rs.getString(1)},${rs.getString(2)},${
            rs
              .getBoolean(3)
          },${rs.getInt(4)},${rs.getBoolean(5)});
            """.stripMargin
      }
      ret.mkString("\n")
    })
}

class Book(val seriesId: Int,
           val rawBookDetailId: Int,
           val name: String,
           val seriesOrder: Int,
           val filePath: String,
           val coverPath: String,
           isInit: Boolean = true)
  extends DBHandler with ToSQL {
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

  override def toSQLNew: String = {
    execute(x => {
      val ret = mutable.MutableList[String]()
      val rs = x.executeQuery(
        s"""
           |SELECT d.series_id,d.raw_book_detail_id,d.name,d.series_order,d.file_path,d.cover_path
           |FROM $dbName d, ${new CrawLog().dbName} c, ${new RawBookDetail().dbName} r
           |WHERE d.raw_book_detail_id=r.id AND r.id > (SELECT raw_id FROM  ${new CrawLog().dbName} ORDER BY id DESC LIMIT 1 OFFSET 1)
           |ORDER BY d.id ASC;
        """.stripMargin)
      while (rs.next()) {
        ret +=
          s"""
             |INSERT INTO $dbName(series_id,raw_book_detail_id,name,series_order,file_path,cover_path)
             |VALUES(${rs.getString(1)},${rs.getString(2)},${rs.getBoolean(3)},${rs.getInt(4)},${rs.getBoolean(5)});
            """.stripMargin
      }
      ret.mkString("\n")
    })
  }

  override def toSQLAll: String = execute(x => {
    val ret = mutable.MutableList[String]()
    val rs = x.executeQuery(
      s"""
         |SELECT series_id,raw_book_detail_id,name,series_order,file_path,cover_path
         |FROM $dbName ORDER BY id ASC;
        """.stripMargin)
    while (rs.next()) {
      ret +=
        s"""
           |INSERT INTO $dbName(series_id,raw_book_detail_id,name,series_order,file_path,cover_path)
           |VALUES(${rs.getString(1)},${rs.getString(2)},${rs.getBoolean(3)},${rs.getInt(4)},${rs.getBoolean(5)});
            """.stripMargin
    }
    ret.mkString("\n")
  })
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

class TagSeriesMap(val seriesId: Int, val tagId: Int, isInit: Boolean = true)
  extends DBHandler {
  override type Fields = (Int, Int, Int)
  override val dbName: String = "tag_series_map"
  override val initDbSql: String =
    s"""
       |CREATE TABLE IF NOT EXISTS $dbName (
       |  id INTEGER PRIMARY KEY AUTOINCREMENT,
       |  series_id INTEGER NOT NULL,
       |  tag_id INTEGER NOT NULL,
       |  FOREIGN KEY(series_id) REFERENCES series(id),
       |  FOREIGN KEY(tag_id) REFERENCES tag(id)
       |);
    """.stripMargin

  def this() = this(0, 0, false)

  override def write(): Unit = {
    if (!isInit) throw new RuntimeException(s"$this not initialized")
    require(seriesId > 0)
    require(tagId > 0)
    val sql = s"INSERT INTO $dbName(series_id,tag_id) values(?,?);"
    prepareExecute(sql, stmt => {
      stmt.setInt(1, seriesId)
      stmt.setInt(2, tagId)
    })
  }

  override def getRow(result: ResultSet): Fields = (
    result.getInt("id"),
    result.getInt("series_id"),
    result.getInt("tag_id")
  )
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
  extends DBHandler
    with ToSQL {
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

  def finish(id: Int): Unit = {
    execute(_.execute(s"UPDATE $dbName SET finished=true WHERE id=$id"))
  }

  def updateDownloadLink(oldURL: String, newURL: String): Unit = {
    execute(
      _ execute s"UPDATE $dbName SET dl_link='$newURL' WHERE dl_link='$oldURL'")
  }

  def latestRecordId: Int = {
    execute(x => {
      val rs =
        x.executeQuery(s"SELECT id from $dbName ORDER BY ID DESC LIMIT 1")
      rs.getInt(1)
    })
  }

  override def toSQLAll: String =
    execute(x => {
      val ret = mutable.MutableList[String]()
      val rs = x.executeQuery(
        s"SELECT name,page_link,img_link,dl_link,finished,dl_link_type FROM $dbName")
      while (rs.next()) {
        ret += s"INSERT $dbName(name,page_link,img_link,dl_link,finished,dl_link_type)" +
          s"VALUES(${rs.getString(1)},${rs.getString(2)},${rs.getString(3)},${
            rs
              .getString(4)
          },${rs.getBoolean(5)},${rs.getInt(6)});"
      }
      ret.mkString("\n")
    })

  override def toSQLNew: String =
    execute(x => {
      val ret = mutable.MutableList[String]()
      val rs = x.executeQuery(
        s"SELECT d.name,d.page_link,d.img_link,d.dl_link,d.finished,d.dl_link_type" +
          s"FROM $dbName d, ${new CrawLog().dbName} c" +
          s"WHERE d.id > (SELECT raw_id FROM ${new CrawLog().dbName} ORDER BY id DESC LIMIT 1 OFFSET 1);"
      )
      while (rs.next()) {
        ret += s"INSERT $dbName(name,page_link,img_link,dl_link,finished,dl_link_type)" +
          s"VALUES(${rs.getString(1)},${rs.getString(2)},${rs.getString(3)},${
            rs
              .getString(4)
          },${rs.getBoolean(5)},${rs.getInt(6)});"
      }
      ret.mkString("\n")
    })
}

class CrawLog(val rawId: Int, isInit: Boolean = true) extends DBHandler {
  override type Fields = (Int, Int, Long)
  override val dbName: String = "craw_log"
  override val initDbSql: String =
    s"""
       |CREATE TABLE IF NOT EXISTS $dbName (
       |  id INTEGER PRIMARY KEY AUTOINCREMENT,
       |  raw_id INTEGER NOT NULL,
       |  time_log NUMERIC NOT NULL
       |);
    """.stripMargin

  def this() = this(0, false)

  override def write(): Unit = {
    if (!isInit) throw new RuntimeException(s"$this not initialized")
    require(rawId > 0)
    val sql =
      s"INSERT INTO $dbName(raw_id,time_log) values(?,?);"
    prepareExecute(
      sql,
      stmt => {
        stmt.setInt(1, rawId)
        stmt.setLong(2, System.currentTimeMillis())
      }
    )
  }

  override def getRow(result: ResultSet): Fields = (
    result.getInt("id"),
    result.getInt("raw_id"),
    result.getLong("time_log")
  )

}
