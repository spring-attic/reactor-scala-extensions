
// Defining dependencies
val reactorVersion = "3.3.8.RELEASE"
val reactorCore = "io.projectreactor" % "reactor-core" % reactorVersion
//test libraries
val micrometer = "io.micrometer" % "micrometer-core" % "1.5.1" % "test"
val mockitoInline = "org.mockito" % "mockito-inline" % "3.3.3" % "test"
val mockitoScala = "org.mockito" %% "mockito-scala" % "1.14.3" % "test"
val scalaTest = "org.scalatest" %% "scalatest" % "3.1.2" % "test"
val reactorTest = "io.projectreactor" % "reactor-test" % reactorVersion % "test"
val catsEffect =  "org.typelevel" %% "cats-effect" % "2.1.3"

//Scala versions for cross compiling
lazy val scala212 = "2.12.11"
lazy val scala213 = "2.13.2"
lazy val supportedScalaVersions = List(scala212, scala213)

ThisBuild / scalaVersion := scala212

lazy val root = (project in file("."))
	.settings(
		crossScalaVersions := supportedScalaVersions,
		name := "reactor-scala-extensions",
		libraryDependencies += reactorCore,
		libraryDependencies += micrometer,
		libraryDependencies += mockitoScala,
		libraryDependencies += scalaTest,
		libraryDependencies += reactorTest,
		libraryDependencies += mockitoInline,
		libraryDependencies += catsEffect
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
coverageMinimum := 83.34
coverageFailOnMinimum := true
