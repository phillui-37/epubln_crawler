name := "epubln_crawler"

version := "1.0"

scalaVersion := "2.12.8"

//mainClass := Some("com.fgoproduction.Main")

// dump template as dom tree
libraryDependencies += "org.jsoup" % "jsoup" % "1.8.3"
// run headless browser to simulate interaction
libraryDependencies += "org.seleniumhq.selenium" % "selenium-java" % "3.141.59"
// sqlite
libraryDependencies += "org.xerial" % "sqlite-jdbc" % "3.25.2"
// restful
libraryDependencies += "com.sparkjava" % "spark-core" % "2.7.2"
// toml
libraryDependencies += "com.moandjiezana.toml" % "toml4j" % "0.7.2"
// velocity template
libraryDependencies += "com.sparkjava" % "spark-template-velocity" % "2.7.1"
// logging
libraryDependencies += "org.slf4j" % "slf4j-simple" % "1.7.25"
// json handling
libraryDependencies += "com.fasterxml.jackson.core" % "jackson-core" % "2.9.8"
libraryDependencies += "com.fasterxml.jackson.core" % "jackson-databind" % "2.9.8"
libraryDependencies += "com.fasterxml.jackson.core" % "jackson-annotations" % "2.9.8"


