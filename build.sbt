name := """tdr-transfer-frontend"""
organization := "tna"
maintainer := "TDRTeam@nationalarchives.gov.uk"

version := "1.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayScala)

watchSources ++= (baseDirectory.value / "npm/src" ** "*").get

scalaVersion := "2.13.13"

libraryDependencies += guice
libraryDependencies += "org.scalatestplus.play" %% "scalatestplus-play" % "5.1.0" % Test

//Needed to run the tests. Prevents incompatible databind version errors.
//More details on a similar error here: https://stackoverflow.com/questions/43841091/spark2-1-0-incompatible-jackson-versions-2-7-6
dependencyOverrides += "com.fasterxml.jackson.core" % "jackson-databind" % "2.11.4" % Test

val playPac4jVersion = "11.1.0-PLAY2.8"
val pac4jVersion = "5.7.3"
val sttpVersion = "2.3.0"

libraryDependencies ++= Seq(
  "org.pac4j" %% "play-pac4j" % playPac4jVersion,
  "org.pac4j" % "pac4j-http" % pac4jVersion exclude ("com.fasterxml.jackson.core", "jackson-databind"),
  "org.pac4j" % "pac4j-oidc" % pac4jVersion exclude ("commons-io", "commons-io") exclude ("com.fasterxml.jackson.core", "jackson-databind"),
  "io.circe" %% "circe-core" % "0.14.6",
  "io.circe" %% "circe-generic" % "0.14.6",
  "com.softwaremill.sttp.client" %% "core" % sttpVersion,
  "com.softwaremill.sttp.client" %% "circe" % sttpVersion,
  "com.softwaremill.sttp.client" %% "async-http-client-backend-future" % sttpVersion,
  "uk.gov.nationalarchives" %% "tdr-graphql-client" % "0.0.151",
  "uk.gov.nationalarchives" %% "tdr-auth-utils" % "0.0.192",
  "uk.gov.nationalarchives" %% "tdr-generated-graphql" % "0.0.367",
  "uk.gov.nationalarchives" %% "tdr-metadata-validation" % "0.0.17",
  "com.github.tototoshi" %% "scala-csv" % "1.3.10",
  "ch.qos.logback" % "logback-classic" % "1.5.2",
  ws,
  "io.opentelemetry" % "opentelemetry-api" % "1.35.0",
  "io.opentelemetry" % "opentelemetry-exporter-otlp" % "1.35.0",
  "io.opentelemetry" % "opentelemetry-sdk" % "1.35.0",
  "io.opentelemetry" % "opentelemetry-extension-aws" % "1.20.1",
  "io.opentelemetry" % "opentelemetry-sdk-extension-aws" % "1.19.0",
  "io.opentelemetry.contrib" % "opentelemetry-aws-xray" % "1.33.0",
  "io.opentelemetry.contrib" % "opentelemetry-aws-xray-propagator" % "1.22.0-alpha",
  "com.github.tomakehurst" % "wiremock-standalone" % "3.0.1" % Test,
  "org.mockito" % "mockito-core" % "5.11.0" % Test,
  "org.scalatestplus" %% "mockito-3-4" % "3.2.10.0" % Test
)

disablePlugins(PlayLogback)
scalacOptions ++= Seq("-language:implicitConversions")

libraryDependencies += play.sbt.PlayImport.cacheApi
libraryDependencies += "com.github.karelcemus" %% "play-redis" % "2.7.0"

pipelineStages := Seq(digest)
