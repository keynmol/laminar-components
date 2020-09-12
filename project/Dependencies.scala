import sbt._
import org.portablescala.sbtplatformdeps.PlatformDepsPlugin.autoImport._

object Versions {
  val Scala = "2.13.3"
  val ScalaGroup = "2.13"

  val cats = "2.1.1"
  val laminar = "0.10.3"
  val http4s = "0.21.6"
  val sttp = "2.2.4"
  val circe = "0.13.0"
  val decline = "1.2.0"
  val organiseImports = "0.4.0"
  val betterMonadicFor = "0.3.1"
  val utest = "0.7.4"
}

object Dependencies {

  val laminar = Def.setting("com.raquo" %%% "laminar" % Versions.laminar)
  val utest = Def.setting("com.lihaoyi" %%% "utest" % Versions.utest % Test)

  object Build {
    val betterMonadicFor =
      "com.olegpy" %% "better-monadic-for" % Versions.betterMonadicFor

    val organizeImports =
      "com.github.liancheng" %% "organize-imports" % Versions.organiseImports
  }

  object Example {
    private val http4sModules =
      Seq("dsl", "blaze-client", "blaze-server", "circe").map("http4s-" + _)

    private val sttpModules = Seq("core", "circe")

    val frontend = Def.setting(
      sttpModules.map("com.softwaremill.sttp.client" %%% _ % Versions.sttp) ++
        Seq(laminar.value) ++
        Seq(utest.value)
    )

    val backend = Def.setting(
      http4sModules.map("org.http4s" %% _ % Versions.http4s) ++
        Seq("com.monovore" %% "decline" % Versions.decline)
    )

    val shared = Def.setting("io.circe" %%% "circe-generic" % Versions.circe)
  }
}
