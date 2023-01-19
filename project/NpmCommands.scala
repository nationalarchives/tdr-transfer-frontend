/** Frontend build commands. Change these if you are using some other package manager. i.e: Yarn
  */
object NpmCommands {
  val dependencyInstall: String = "npm ci"
  val build: String = s"npm run build"

  val buildDev: String = "npm run build-ts"
}
