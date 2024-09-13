name := """tdr-transfer-frontend"""
organization := "tna"
maintainer := "TDRTeam@nationalarchives.gov.uk"

version := "1.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayScala)

watchSources ++= (baseDirectory.value / "npm/src" ** "*").get

scalaVersion := "2.13.14"

libraryDependencies += guice
libraryDependencies += "org.scalatestplus.play" %% "scalatestplus-play" % "5.1.0" % Test

val playVersion = "3.0.3"
val playPac4jVersion = "12.0.0-PLAY3.0"
val pac4jVersion = "6.0.5"
val sttpVersion = "2.3.0"

libraryDependencies ++= Seq(
  "org.pac4j" %% "play-pac4j" % playPac4jVersion excludeAll (ExclusionRule("commons-io", "commons-io"), ExclusionRule(organization = "com.fasterxml.jackson.core")),
  "org.pac4j" % "pac4j-http" % pac4jVersion excludeAll (ExclusionRule(organization = "com.fasterxml.jackson.core")),
  "org.pac4j" % "pac4j-oidc" % pac4jVersion,
  "io.circe" %% "circe-core" % "0.14.10",
  "io.circe" %% "circe-generic" % "0.14.10",
  "com.softwaremill.sttp.client" %% "core" % sttpVersion,
  "com.softwaremill.sttp.client" %% "circe" % sttpVersion,
  "com.softwaremill.sttp.client" %% "async-http-client-backend-future" % sttpVersion,
  "uk.gov.nationalarchives" %% "tdr-graphql-client" % "0.0.180",
  "uk.gov.nationalarchives" %% "tdr-auth-utils" % "0.0.212",
  "uk.gov.nationalarchives" %% "tdr-generated-graphql" % "0.0.387",
  "uk.gov.nationalarchives" %% "tdr-metadata-validation" % "0.0.54",
  "uk.gov.nationalarchives" %% "s3-utils" % "0.1.205",
  "uk.gov.nationalarchives" %% "sns-utils" % "0.1.205",
  "ch.qos.logback" % "logback-classic" % "1.5.8",
  ws,
  "io.opentelemetry" % "opentelemetry-api" % "1.42.1",
  "io.opentelemetry" % "opentelemetry-exporter-otlp" % "1.42.1",
  "io.opentelemetry" % "opentelemetry-sdk" % "1.42.1",
  "io.opentelemetry" % "opentelemetry-extension-aws" % "1.20.1",
  "io.opentelemetry" % "opentelemetry-sdk-extension-aws" % "1.19.0",
  "io.opentelemetry.contrib" % "opentelemetry-aws-xray" % "1.38.0",
  "io.opentelemetry.contrib" % "opentelemetry-aws-xray-propagator" % "1.22.0-alpha",
  "com.github.tomakehurst" % "wiremock-standalone" % "3.0.1" % Test,
  "org.mockito" % "mockito-core" % "5.13.0" % Test,
  "org.scalatestplus" %% "mockito-3-4" % "3.2.10.0" % Test
)
libraryDependencies += "org.scala-lang.modules" %% "scala-java8-compat" % "1.0.2"

dependencyOverrides += "com.fasterxml.jackson.core" % "jackson-databind" % "2.17.0"
dependencyOverrides += "com.fasterxml.jackson.core" % "jackson-core" % "2.17.0"

disablePlugins(PlayLogback)
scalacOptions ++= Seq("-language:implicitConversions")

libraryDependencies += play.sbt.PlayImport.cacheApi
libraryDependencies += "com.github.karelcemus" %% "play-redis" % "5.0.0"

libraryDependencies += "org.dhatim" % "fastexcel" % "0.18.3"
libraryDependencies += "org.dhatim" % "fastexcel-reader" % "0.18.3"

excludeDependencies ++= Seq(
  ExclusionRule(organization = "com.typesafe.akka"),
  ExclusionRule(organization = "com.typesafe.play")
)

pipelineStages := Seq(digest)
