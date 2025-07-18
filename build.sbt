name := """tdr-transfer-frontend"""
organization := "tna"
maintainer := "TDRTeam@nationalarchives.gov.uk"

version := "1.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayScala)

watchSources ++= (baseDirectory.value / "npm/src" ** "*").get

scalaVersion := "2.13.16"

libraryDependencies += guice
libraryDependencies += "org.scalatestplus.play" %% "scalatestplus-play" % "7.0.2" % Test

val playVersion = "3.0.3"
val playPac4jVersion = "12.0.2-PLAY3.0"
val pac4jVersion = "6.2.0"
val sttpVersion = "3.11.0"

libraryDependencies ++= Seq(
  "org.pac4j" %% "play-pac4j" % playPac4jVersion excludeAll (ExclusionRule("commons-io", "commons-io"), ExclusionRule(organization = "com.fasterxml.jackson.core")),
  "org.pac4j" % "pac4j-http" % pac4jVersion excludeAll (ExclusionRule(organization = "com.fasterxml.jackson.core")),
  "org.pac4j" % "pac4j-oidc" % pac4jVersion,
  "io.circe" %% "circe-core" % "0.14.14",
  "io.circe" %% "circe-generic" % "0.14.14",
  "com.softwaremill.sttp.client3" %% "core" % sttpVersion,
  "com.softwaremill.sttp.client3" %% "circe" % sttpVersion,
  "com.softwaremill.sttp.client3" %% "async-http-client-backend-future" % sttpVersion,
  "uk.gov.nationalarchives" %% "tdr-graphql-client" % "0.0.240",
  "uk.gov.nationalarchives" %% "tdr-auth-utils" % "0.0.249",
  "uk.gov.nationalarchives" %% "tdr-generated-graphql" % "0.0.423",
  "uk.gov.nationalarchives" % "da-metadata-schema_2.13" % "0.0.66",
  "uk.gov.nationalarchives" %% "tdr-metadata-validation" % "0.0.155",
  "uk.gov.nationalarchives" %% "s3-utils" % "0.1.283",
  "uk.gov.nationalarchives" %% "sns-utils" % "0.1.283",
  "ch.qos.logback" % "logback-classic" % "1.5.18",
  ws,
  "io.opentelemetry" % "opentelemetry-api" % "1.49.0",
  "io.opentelemetry" % "opentelemetry-exporter-otlp" % "1.49.0",
  "io.opentelemetry" % "opentelemetry-sdk" % "1.49.0",
  "io.opentelemetry" % "opentelemetry-extension-aws" % "1.20.1",
  "io.opentelemetry" % "opentelemetry-sdk-extension-aws" % "1.19.0",
  "io.opentelemetry.contrib" % "opentelemetry-aws-xray" % "1.47.0",
  "io.opentelemetry.contrib" % "opentelemetry-aws-xray-propagator" % "1.46.0-alpha",
  "com.github.tomakehurst" % "wiremock-standalone" % "3.0.1" % Test,
  "org.mockito" % "mockito-core" % "5.18.0" % Test,
  "org.scalatestplus" %% "mockito-3-4" % "3.2.10.0" % Test
)
libraryDependencies += "org.scala-lang.modules" %% "scala-java8-compat" % "1.0.2"

dependencyOverrides += "com.fasterxml.jackson.core" % "jackson-databind" % "2.19.1"
dependencyOverrides += "com.fasterxml.jackson.core" % "jackson-core" % "2.19.1"

disablePlugins(PlayLogback)
scalacOptions ++= Seq("-language:implicitConversions")

libraryDependencies += play.sbt.PlayImport.cacheApi
libraryDependencies += "com.github.karelcemus" %% "play-redis" % "5.3.0"

libraryDependencies += "org.dhatim" % "fastexcel" % "0.19.0"
libraryDependencies += "org.dhatim" % "fastexcel-reader" % "0.19.0"

excludeDependencies ++= Seq(
  ExclusionRule(organization = "com.typesafe.akka"),
  ExclusionRule(organization = "com.typesafe.play")
)

pipelineStages := Seq(digest)
