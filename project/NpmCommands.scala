/**
  * Frontend build commands.
  * Change these if you are using some other package manager. i.e: Yarn
  */
object NpmCommands {
  val dependencyInstall: String = "npm ci"
  val test: String = "npm run test"
  def build(stage: String): String = s"npm run build:$stage"

  val buildDev: String = "npm run build-ts"
}