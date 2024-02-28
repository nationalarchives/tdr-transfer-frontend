addSbtPlugin("com.typesafe.play" % "sbt-plugin" % "2.9.1")
addSbtPlugin("org.scalameta" % "sbt-scalafmt" % "2.5.2")
resolvers += Resolver.jcenterRepo
addSbtPlugin("com.github.sbt" % "sbt-digest" % "2.0.0")
libraryDependencySchemes += "org.scala-lang.modules" %% "scala-xml" % VersionScheme.Always
