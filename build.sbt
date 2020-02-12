name := """tdr-transfer-frontend"""
organization := "tna"

version := "1.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayScala, GraphQLCodegenPlugin)

scalaVersion := "2.12.8"

libraryDependencies += guice
libraryDependencies += "org.scalatestplus.play" %% "scalatestplus-play" % "4.0.3" % Test

// Adds additional packages into Twirl
//TwirlKeys.templateImports += "tna.controllers._"

// Adds additional packages into conf/routes
// play.sbt.routes.RoutesKeys.routesImport += "tna.binders._"

graphqlSchemas += GraphQLSchema(
  "consignmentapi",
  "consignmentapi schema",
  Def.task(
    GraphQLSchemaLoader
      .fromFile(new File("conf/schema.graphql"))
      .loadSchema()
  ).taskValue)

graphqlCodegenStyle := Apollo
graphqlCodegenJson := JsonCodec.Circe
graphqlCodegenSchema := graphqlRenderSchema.toTask("consignmentapi").value


val playPac4jVersion = "8.0.1"
val pac4jVersion = "3.7.0"
val akkaVersion = "2.6.3"

libraryDependencies ++= Seq(
  "org.pac4j" %% "play-pac4j" % playPac4jVersion,
  "org.pac4j" % "pac4j-http" % pac4jVersion,
  "org.pac4j" % "pac4j-oidc" % pac4jVersion exclude("commons-io", "commons-io"),
  "com.typesafe.play" % "play-cache_2.12" % "2.7.2",
  "org.apache.shiro" % "shiro-core" % "1.4.0",
  "net.bytebuddy" % "byte-buddy" % "1.9.7",
  "org.sangria-graphql" %% "sangria" % "2.0.0-M3",
  "io.circe" %% "circe-core" % "0.13.0",
  "io.circe" %% "circe-generic" % "0.13.0",
  "com.softwaremill.sttp.client" %% "core" % "2.0.0-RC9",
  "com.softwaremill.sttp.client" %% "circe" % "2.0.0-RC9",
  "com.softwaremill.sttp.client" %% "async-http-client-backend-future" % "2.0.0-RC9",
   "uk.gov.nationalarchives.tdr" %% "tdr-graphql-client" % "0.1.0-SNAPSHOT",
  "uk.gov.nationalarchives.tdr" %% "tdr-auth-utils" % "0.1.0-SNAPSHOT"

)

libraryDependencies += play.sbt.PlayImport.cacheApi
libraryDependencies += "com.github.karelcemus" %% "play-redis" % "2.5.0"