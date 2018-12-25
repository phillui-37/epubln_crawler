name := "epubln_crawler"

version := "0.1"

scalaVersion := "2.12.8"

//mainClass := Some("com.fgoproduction.Main")

// dump html as dom tree
libraryDependencies += "org.jsoup" % "jsoup" % "1.8.3"
// run headless browser to simulate interaction
libraryDependencies += "org.seleniumhq.selenium" % "selenium-java" % "3.141.59"
// sqlite
libraryDependencies += "org.xerial" % "sqlite-jdbc" % "3.25.2"
// restful
libraryDependencies += "com.sparkjava" % "spark-core" % "2.7.2"