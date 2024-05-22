ivyLoggingLevel := UpdateLogging.Full
logLevel := Level.Debug

addSbtPlugin("org.playframework" % "sbt-plugin" % "3.0.3")
addSbtPlugin("org.scalameta" % "sbt-scalafmt" % "2.5.2")
resolvers += Resolver.jcenterRepo
addSbtPlugin("com.github.sbt" % "sbt-digest" % "2.0.0")
libraryDependencySchemes += "org.scala-lang.modules" %% "scala-xml" % VersionScheme.Always
