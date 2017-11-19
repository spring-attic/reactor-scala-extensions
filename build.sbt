lazy val root = (project in file("."))
  .settings(
    name         := "reactor-scala-extensions",
    organization := "io.projectreactor",
    scalaVersion := "2.12.4",
    version      := "0.3.2-SNAPSHOT"
  )

fork := true

crossScalaVersions := Seq("2.12.4", "2.11.11")

libraryDependencies += "io.projectreactor" % "reactor-core" % "3.1.1.RELEASE"

libraryDependencies += "org.scalatest" %% "scalatest" % "3.0.4" % "test"
libraryDependencies += "io.projectreactor" % "reactor-test" % "3.1.1.RELEASE" % "test"
libraryDependencies += "org.mockito" % "mockito-core" % "2.12.0" % "test"

coverageEnabled := true

publishTo := {
  val nexus = "https://oss.sonatype.org/"
  if(isSnapshot.value)
    Some("snapshots" at nexus + "content/repositories/snapshots")
  else
    Some("releases" at nexus + "service/local/staging/deploy/maven2")
}

pomExtra :=
  <developers>
    <developer>
      <id>sinwe</id>
      <name>Winarto</name>
      <email>winarto@gmail.com</email>
    </developer>
  </developers>
  <scm>
    <connection>scm:git:git@github.com:reactor/reactor-scala-extensions.git</connection>
    <developerConnection>scm:git:git@github.com:reactor/reactor-scala-extensions.git</developerConnection>
    <url>https://github.com/reactor/reactor-scala-extensions</url>
  </scm>
  <licenses>
    <license>
      <name>Apache License, Version 2.0, January 2004</name>
      <url>https://www.apache.org/licenses/LICENSE-2.0.html</url>
    </license>
  </licenses>

useGpg := true
usePgpKeyHex("C72C04DF")