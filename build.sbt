
// Defining dependencies
val reactorVersion = "3.3.4.RELEASE"
val reactorCore = "io.projectreactor" % "reactor-core" % reactorVersion
val scalaCollCompat = "org.scala-lang.modules" %% "scala-collection-compat" % "2.1.4"
//test libraries
val mockitoInline = "org.mockito" % "mockito-inline" % "3.3.0" % "test"
val mockitoScala = "org.mockito" %% "mockito-scala" % "1.11.3" % "test"
val scalatest = "org.scalatest" %% "scalatest" % "3.1.1" % "test"
val reactorTest = "io.projectreactor" % "reactor-test" % reactorVersion % "test"

//Scala versions for cross compiling
lazy val scala211 = "2.11.12"
lazy val scala212 = "2.12.10"
lazy val scala213 = "2.13.1"
lazy val supportedScalaVersions = List(scala211, scala212, scala213)

ThisBuild / scalaVersion := "2.12.10"
//ThisBuild / version      := "0.6.1.0-SNAPSHOT"

lazy val root = (project in file("."))
	.settings(
		crossScalaVersions := supportedScalaVersions,
		name := "reactor-scala-extensions",
		libraryDependencies += reactorCore,
		libraryDependencies += scalaCollCompat,
		libraryDependencies += mockitoScala,
		libraryDependencies += scalatest,
		libraryDependencies += reactorTest,
		libraryDependencies += mockitoInline
	)

// Prevent test being executed in parallel as it may failed for the Scheduler and Virtual Scheduler
Test / parallelExecution := false

// Scala Compiler Options
scalacOptions ++= Seq(
	"-target:jvm-1.8"
)

// Scaladoc compiler options
Compile / doc / scalacOptions ++= Seq(
	"-no-link-warnings"
)
autoAPIMappings := true

// SCoverage
//coverageEnabled := true
coverageMinimum := 80.74
coverageFailOnMinimum := true