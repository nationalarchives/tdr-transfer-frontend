import scala.sys.process.Process

PlayKeys.playRunHooks += baseDirectory.map(NpmRunHook.apply).value

val Success = 0
val Error = 1

def runOnCommandline(script: String)(implicit dir: File): Int = Process(script, dir) !

def runNpmInstall(implicit dir: File): Int =
  runOnCommandline(NpmCommands.dependencyInstall)

// Execute task if node modules are installed, else return Error status.
def ifNodeModulesInstalled(task: => Int)(implicit dir: File): Int =
  if (runNpmInstall == Success) {
    task
  } else {
    Error
  }

def executeProdBuild(implicit dir: File): Int = ifNodeModulesInstalled(runOnCommandline(NpmCommands.build))

lazy val `ts-prod-build` = taskKey[Unit]("Run TS build when packaging the application.")

`ts-prod-build` := {
  implicit val userInterfaceRoot: File = baseDirectory.value / "npm"
  if (executeProdBuild != Success) throw new Exception("Oops! TS Build crashed.")
}

dist := (dist dependsOn `ts-prod-build`).value
