import play.sbt.PlayRunHook
import sbt._

import scala.sys.process.Process

/** Frontend build play run hook. https://www.playframework.com/documentation/2.8.x/SBTCookbook
  */
object NpmRunHook {
  def apply(base: File): PlayRunHook = {
    object TSBuildHook extends PlayRunHook {

      var process: Option[Process] = None

      /** Change the commands in `FrontendCommands.scala` if you want to use Yarn.
        */
      var install: String = NpmCommands.dependencyInstall
      var run: String = NpmCommands.buildDev

      /** Executed before play run start. Run npm install if node modules are not installed.
        */
      override def beforeStarted(): Unit = {
        if (!(base / "npm" / "node_modules").exists()) Process(install, base / "npm").!
      }

      /** Executed after play run start. Run npm start
        */
      override def afterStarted(): Unit = {
        Process(run, base / "npm").run
      }

      /** Executed after play run stop. Cleanup frontend execution processes.
        */
      override def afterStopped(): Unit = {
        process.foreach(_.destroy())
        process = None
      }

    }

    TSBuildHook
  }
}
