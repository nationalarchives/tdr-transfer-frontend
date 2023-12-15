addSbtPlugin("com.typesafe.play" % "sbt-plugin" % "2.9.0")
addSbtPlugin("org.scalameta" % "sbt-scalafmt" % "2.5.2")
resolvers += Resolver.jcenterRepo
addSbtPlugin("com.typesafe.sbt" % "sbt-digest" % "1.1.4")
libraryDependencySchemes += "org.scala-lang.modules" %% "scala-xml" % VersionScheme.Always
