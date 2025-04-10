name := """tdr-transfer-frontend"""
organization := "tna"
maintainer := "TDRTeam@nationalarchives.gov.uk"

version := "1.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayScala)

watchSources ++= (baseDirectory.value / "npm/src" ** "*").get

scalaVersion := "2.13.16"

libraryDependencies += guice
libraryDependencies += "org.scalatestplus.play" %% "scalatestplus-play" % "7.0.1" % Test

val playVersion = "3.0.3"
val playPac4jVersion = "12.0.2-PLAY3.0"
val pac4jVersion = "6.0.5"
val sttpVersion = "3.10.3"

libraryDependencies ++= Seq(
  "org.pac4j" %% "play-pac4j" % playPac4jVersion excludeAll (ExclusionRule("commons-io", "commons-io"), ExclusionRule(organization = "com.fasterxml.jackson.core")),
  "org.pac4j" % "pac4j-http" % pac4jVersion excludeAll (ExclusionRule(organization = "com.fasterxml.jackson.core")),
  "org.pac4j" % "pac4j-oidc" % pac4jVersion,
  "io.circe" %% "circe-core" % "0.14.12",
  "io.circe" %% "circe-generic" % "0.14.12",
  "com.softwaremill.sttp.client3" %% "core" % sttpVersion,
  "com.softwaremill.sttp.client3" %% "circe" % sttpVersion,
  "com.softwaremill.sttp.client3" %% "async-http-client-backend-future" % sttpVersion,
  "uk.gov.nationalarchives" %% "tdr-graphql-client" % "0.0.221",
  "uk.gov.nationalarchives" %% "tdr-auth-utils" % "0.0.236",
  "uk.gov.nationalarchives" %% "tdr-generated-graphql" % "0.0.406",
  "uk.gov.nationalarchives" %% "tdr-metadata-validation" % "0.0.118",
  "uk.gov.nationalarchives" %% "s3-utils" % "0.1.242",
  "uk.gov.nationalarchives" %% "sns-utils" % "0.1.242",
  "ch.qos.logback" % "logback-classic" % "1.5.18",
  ws,
  "io.opentelemetry" % "opentelemetry-api" % "1.45.0",
  "io.opentelemetry" % "opentelemetry-exporter-otlp" % "1.45.0",
  "io.opentelemetry" % "opentelemetry-sdk" % "1.45.0",
  "io.opentelemetry" % "opentelemetry-extension-aws" % "1.20.1",
  "io.opentelemetry" % "opentelemetry-sdk-extension-aws" % "1.19.0",
  "io.opentelemetry.contrib" % "opentelemetry-aws-xray" % "1.45.0",
  "io.opentelemetry.contrib" % "opentelemetry-aws-xray-propagator" % "1.41.0-alpha",
  "com.github.tomakehurst" % "wiremock-standalone" % "3.0.1" % Test,
  "org.mockito" % "mockito-core" % "5.16.1" % Test,
  "org.scalatestplus" %% "mockito-3-4" % "3.2.10.0" % Test
)
libraryDependencies += "org.scala-lang.modules" %% "scala-java8-compat" % "1.0.2"

dependencyOverrides += "com.fasterxml.jackson.core" % "jackson-databind" % "2.18.3"
dependencyOverrides += "com.fasterxml.jackson.core" % "jackson-core" % "2.18.3"

disablePlugins(PlayLogback)
scalacOptions ++= Seq("-language:implicitConversions")

libraryDependencies += play.sbt.PlayImport.cacheApi
libraryDependencies += "com.github.karelcemus" %% "play-redis" % "5.1.0"

libraryDependencies += "org.dhatim" % "fastexcel" % "0.18.4"
libraryDependencies += "org.dhatim" % "fastexcel-reader" % "0.18.4"

excludeDependencies ++= Seq(
  ExclusionRule(organization = "com.typesafe.akka"),
  ExclusionRule(organization = "com.typesafe.play")
)

pipelineStages := Seq(digest)
