
inThisBuild(
  Seq(
    scalafixDependencies += Dependencies.Build.organizeImports,
    semanticdbEnabled := true,
    semanticdbVersion := scalafixSemanticdb.revision,
    scalafixScalaBinaryVersion := Versions.ScalaGroup
  )
)

lazy val root =
  (project in file(".")).aggregate(websocket, exampleFrontend, exampleBackend, exampleShared.js, exampleShared.jvm)

lazy val websocket = (project in file("modules/websocket"))
  .enablePlugins(ScalaJSPlugin)
  .settings(
    libraryDependencies ++= Dependencies.Example.frontend.value,
    libraryDependencies += Dependencies.utest.value,
    testFrameworks += new TestFramework("utest.runner.Framework"),
    jsEnv in Test := new org.scalajs.jsenv.jsdomnodejs.JSDOMNodeJSEnv()
  )
  .settings(commonBuildSettings)


lazy val exampleFrontend = (project in file("example/frontend"))
  .dependsOn(exampleShared.js)
  .dependsOn(websocket)
  .enablePlugins(ScalaJSPlugin)
  .settings(scalaJSUseMainModuleInitializer := true)
  .settings(
    libraryDependencies ++= Dependencies.Example.frontend.value,
    testFrameworks += new TestFramework("utest.runner.Framework"),
    jsEnv in Test := new org.scalajs.jsenv.jsdomnodejs.JSDOMNodeJSEnv()
  )
  .settings(commonBuildSettings)

lazy val exampleBackend = (project in file("example/backend"))
  .dependsOn(exampleShared.jvm)
  .settings(libraryDependencies ++= Dependencies.Example.backend.value)
  .settings(commonBuildSettings)
  .enablePlugins(JavaAppPackaging)
  .enablePlugins(DockerPlugin)
  .settings(
    mappings in Universal += {
      val appJs = (exampleFrontend / Compile / fullOptJS).value.data
      appJs -> ("lib/prod.js")
    },
    javaOptions in Universal ++= Seq(
      "--port 8080",
      "--mode prod"
    ),
    packageName in Docker := "laminar-components-example"
  )

lazy val exampleShared = crossProject(JSPlatform, JVMPlatform)
  .crossType(CrossType.Pure)
  .in(file("example/shared"))
  .jvmSettings(libraryDependencies ++= Dependencies.Example.shared.value)
  .jsSettings(libraryDependencies ++= Dependencies.Example.shared.value)
  .jsSettings(commonBuildSettings)
  .jvmSettings(commonBuildSettings)

lazy val fastOptCompileCopy = taskKey[Unit]("")

val jsPath = "example/backend/src/main/resources"

fastOptCompileCopy := {
  val source = (exampleFrontend / Compile / fastOptJS).value.data
  IO.copyFile(
    source,
    baseDirectory.value / jsPath / "dev.js"
  )
}

lazy val fullOptCompileCopy = taskKey[Unit]("")

fullOptCompileCopy := {
  val source = (exampleFrontend / Compile / fullOptJS).value.data
  IO.copyFile(
    source,
    baseDirectory.value / jsPath / "prod.js"
  )

}

lazy val commonBuildSettings: Seq[Def.Setting[_]] = Seq(
  scalaVersion := Versions.Scala,
  addCompilerPlugin(Dependencies.Build.betterMonadicFor),
  scalacOptions ++= Seq(
    "-Ywarn-unused"
  )
)

addCommandAlias("runExampleDev", ";fastOptCompileCopy; exampleBackend/reStart --mode dev")
addCommandAlias("runExampleProd", ";fullOptCompileCopy; exampleBackend/reStart --mode prod")

val scalafixRules = Seq(
  "OrganizeImports",
  "DisableSyntax",
  "LeakingImplicitClassVal",
  "ProcedureSyntax",
  "NoValInForComprehension"
).mkString(" ")

val CICommands = Seq(
  "clean",
  "backend/compile",
  "backend/test",
  "frontend/compile",
  "frontend/fastOptJS",
  "frontend/test",
  "scalafmtCheckAll",
  s"scalafix --check $scalafixRules"
).mkString(";")

val PrepareCICommands = Seq(
  s"compile:scalafix --rules $scalafixRules",
  s"test:scalafix --rules $scalafixRules",
  "test:scalafmtAll",
  "compile:scalafmtAll",
  "scalafmtSbt"
).mkString(";")

addCommandAlias("ci", CICommands)

addCommandAlias("preCI", PrepareCICommands)
