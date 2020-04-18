releaseCrossBuild := true

// strategy to increase version
// see https://github.com/sbt/sbt-release#convenient-versioning
releaseVersionBump := sbtrelease.Version.Bump.Next

// sign released version
releasePublishArtifactsAction := PgpKeys.publishSigned.value
