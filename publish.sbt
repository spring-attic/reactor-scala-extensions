ThisBuild / organization := "io.projectreactor"
ThisBuild / organizationHomepage := Some(url("https://github.com/reactor/reactor-scala-extensions/"))

ThisBuild / scmInfo := Some(
  ScmInfo(
    url("https://github.com/reactor/reactor-scala-extensions"),
    "scm:git:git@github.com:reactor/reactor-scala-extensions.git"
  )
)

ThisBuild / developers := List(
  Developer(
    id    = "sinwe",
    name  = "Winarto",
    email = "winarto@gmail.com",
    url   = url("https://github.com/sinwe")
  )
)

ThisBuild / description := "A scala extensions for Project Reactor Flux and Mono so that the code can be fluently used in Scala"
ThisBuild / licenses := List("Apache-2.0" -> new URL("https://www.apache.org/licenses/LICENSE-2.0.html"))
ThisBuild / homepage := Some(url("https://github.com/reactor/reactor-scala-extensions"))

// Remove all additional repository other than Maven Central from POM
ThisBuild / pomIncludeRepository := { _ => false }

// Publishing to Sonatype
ThisBuild / publishTo := {
  val nexus = "https://oss.sonatype.org/"
  if (isSnapshot.value) Some("Sonatype Snapshots Nexus" at nexus + "content/repositories/snapshots")
  else Some("Sonatype Release Nexus" at nexus + "service/local/staging/deploy/maven2")
}
ThisBuild / publishMavenStyle := true

// Signing
useGpg := true
pgpSecretRing := pgpPublicRing.value