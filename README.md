# reactor-scala-extensions is no longer actively maintained by VMware, Inc.
This project is being move to the `spring-attic` GitHub org, and may eventually reside in the `vmware-archive` org.

Special note from Simon Baslé @simonbasle, Reactor Project Lead:
Should this project need to be revived, please reach out to the Reactor GitHub org @team + GitHub user @sinwe in a GitHub discussion or issue, or on Twitter to @projectreactor or @springops


# Reactor Scala Extensions
[![Join the chat at https://gitter.im/reactor/reactor-scala-extensions](https://badges.gitter.im/reactor/reactor-scala-extensions.svg)](https://gitter.im/reactor/reactor-scala-extensions?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)
[![Reactor Scala Extensions](https://maven-badges.herokuapp.com/maven-central/io.projectreactor/reactor-scala-extensions_2.12/badge.svg?style=plastic)](https://mvnrepository.com/artifact/io.projectreactor/reactor-scala-extensions_2.12)
[![Latest](https://img.shields.io/github/release/reactor/reactor-scala-extensions/all.svg)]() 
[![Download](https://api.bintray.com/packages/sinwe/io.projectreactor/reactor-scala-extensions_2.12/images/download.svg) ](https://bintray.com/sinwe/io.projectreactor/reactor-scala-extensions_2.12/_latestVersion)

[![Build Status](https://travis-ci.com/reactor/reactor-scala-extensions.svg?branch=master)](https://travis-ci.com/reactor/reactor-scala-extensions)
![](https://github.com/reactor/reactor-scala-extensions/workflows/Scala%20CI/badge.svg)
[![codecov](https://codecov.io/gh/reactor/reactor-scala-extensions/branch/master/graph/badge.svg)](https://codecov.io/gh/reactor/reactor-scala-extensions)

[![Average time to resolve an issue](http://isitmaintained.com/badge/resolution/reactor/reactor-scala-extensions.svg)](http://isitmaintained.com/project/reactor/reactor-scala-extensions "Average time to resolve an issue")
[![Percentage of issues still open](http://isitmaintained.com/badge/open/reactor/reactor-scala-extensions.svg)](http://isitmaintained.com/project/reactor/reactor-scala-extensions "Percentage of issues still open")
                            
This project is a Scala extension for [reactor-core](https://github.com/reactor/reactor-core).

Using reactor-core project as it is in scala code will look ugly because
a lot of methods use Java 8 lambda which is not compatible with Scala lambda.
This will force Scala code to use anonymous class which turns ugly.

So instead of

    val mono = Mono.just(1)
                   .map(new java.util.function.Function[Int, String] {
                       def apply(t: Int): String = t.toString
                   })
                   
it becomes

    val mono = SMono.just(1).map(_.toString)

This extension will also return scala's `scala.collection.immutable.Stream` instead of Java's `java.util.stream.Stream`
and `scala.concurrent.Future` instead of `java.util.concurrent.CompletableFuture`
## Getting it

With SBT:

    libraryDependencies += "io.projectreactor" %% "reactor-scala-extensions" % "0.7.0"

With Gradle:
    
    repositories {
        //maven { url 'https://oss.sonatype.org/content/repositories/snapshots/' }
        mavenCentral()
    }
    
    dependencies {
        //compile "io.projectreactor:reactor-scala-extensions_2.12:0.7.1-SNAPSHOT
        //compile "io.projectreactor:reactor-scala-extensions_2.13:0.7.0 //for scala 2.13
        compile "io.projectreactor:reactor-scala-extensions_2.12:0.7.0 //for scala 2.12
        //compile "io.projectreactor:reactor-scala-extensions_2.11:0.7.0 //for scala 2.11
    }

With Maven:

    <!-- To get latest SNAPSHOT version from Sonatype
    <repositories>
        <repository>
            <snapshots>
                <enabled>true</enabled>
            </snapshots>
            <id>ossSonatypeSnapshot</id>
            <name>OSS Sonatype Snapshots</name>
            <url>https://oss.sonatype.org/content/repositories/snapshots/</url>
            <layout>default</layout>
        </repository>
     </repositories>

    <dependency>
        <groupId>io.projectreactor</groupId>
        <artifactId>reactor-scala-extensions</artifactId>
        <version>0.7.1-SNAPSHOT</version>
    </dependency>
    -->
    <dependency>
        <groupId>io.projectreactor</groupId>
        <artifactId>reactor-scala-extensions_2.12</artifactId> <!-- for scala 2.12 -->
        <!--<artifactId>reactor-scala-extensions_2.11</artifactId> for scala 2.11 -->
        <!--<artifactId>reactor-scala-extensions_2.13</artifactId> for scala 2.13 -->
        <version>0.7.0</version>
    </dependency>

## Contributing
Contributions are welcome. Simply fork this project, make some modification, push and 
create a pull request.
