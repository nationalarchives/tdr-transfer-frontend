name := """tdr-transfer-frontend"""
organization := "tna"
maintainer := "TDRTeam@nationalarchives.gov.uk"

version := "1.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayScala)

watchSources ++= (baseDirectory.value / "npm/src" ** "*").get

scalaVersion := "2.13.18"

libraryDependencies += guice
libraryDependencies += "org.scalatestplus.play" %% "scalatestplus-play" % "7.0.2" % Test

val playVersion = "3.0.3"
val playPac4jVersion = "13.0.3-PLAY3.0"
val pac4jVersion = "6.5.6"
val sttpVersion = "3.11.0"
val awsUtilsVersion = "0.1.339"

libraryDependencies ++= Seq(
  "org.pac4j" %% "play-pac4j" % playPac4jVersion excludeAll (ExclusionRule("commons-io", "commons-io"), ExclusionRule(organization = "com.fasterxml.jackson.core")),
  "org.pac4j" % "pac4j-http" % pac4jVersion excludeAll ExclusionRule(organization = "com.fasterxml.jackson.core"),
  "org.pac4j" % "pac4j-oidc" % pac4jVersion,
  "io.circe" %% "circe-core" % "0.14.16",
  "io.circe" %% "circe-generic" % "0.14.16",
  "com.softwaremill.sttp.client3" %% "core" % sttpVersion,
  "com.softwaremill.sttp.client3" %% "circe" % sttpVersion,
  "com.softwaremill.sttp.client3" %% "async-http-client-backend-future" % sttpVersion,
  "uk.gov.nationalarchives" %% "tdr-graphql-client" % "0.0.306",
  "uk.gov.nationalarchives" %% "tdr-auth-utils" % "0.0.298",
  "uk.gov.nationalarchives" %% "tdr-generated-graphql" % "0.0.485",
  "uk.gov.nationalarchives" %% "tdr-statuses" % "0.0.48",
  "uk.gov.nationalarchives" %% "tdr-service-inputs" % "0.0.48",
  "uk.gov.nationalarchives" % "da-metadata-schema_2.13" % "0.0.139",
  "uk.gov.nationalarchives" %% "tdr-metadata-validation" % "0.0.238",
  "uk.gov.nationalarchives" %% "s3-utils" % awsUtilsVersion,
  "uk.gov.nationalarchives" %% "sns-utils" % awsUtilsVersion,
  "uk.gov.nationalarchives" %% "stepfunction-utils" % awsUtilsVersion,
  "uk.gov.nationalarchives" %% "tdr-state-control" % "0.0.48",
  "ch.qos.logback" % "logback-classic" % "1.6.3",
  ws,
  "com.github.tomakehurst" % "wiremock-standalone" % "3.0.1" % Test,
  "org.mockito" % "mockito-core" % "5.23.0" % Test,
  "org.scalatestplus" %% "mockito-3-4" % "3.2.10.0" % Test,
  "org.jsoup" % "jsoup" % "1.23.1" % Test
)
libraryDependencies += "org.scala-lang.modules" %% "scala-java8-compat" % "1.0.2"

dependencyOverrides += "com.fasterxml.jackson.core" % "jackson-databind" % "2.22.2"
dependencyOverrides += "com.fasterxml.jackson.core" % "jackson-core" % "2.22.2"
dependencyOverrides += "org.scala-lang" % "scala-library" % scalaVersion.value

disablePlugins(PlayLogback)
scalacOptions ++= Seq("-language:implicitConversions")

libraryDependencies += play.sbt.PlayImport.cacheApi
libraryDependencies += "com.github.karelcemus" %% "play-redis" % "5.4.0"

libraryDependencies += "org.dhatim" % "fastexcel" % "0.20.2"
libraryDependencies += "org.dhatim" % "fastexcel-reader" % "0.20.2"
libraryDependencies += "com.github.tototoshi" %% "scala-csv" % "2.0.0"

excludeDependencies ++= Seq(
  ExclusionRule(organization = "com.typesafe.akka"),
  ExclusionRule(organization = "com.typesafe.play")
)

(Test / envVars) := Map("AWS_ACCESS_KEY_ID" -> "test", "AWS_SECRET_ACCESS_KEY" -> "test")

pipelineStages := Seq(digest)
