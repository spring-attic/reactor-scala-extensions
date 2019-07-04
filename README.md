# Reactor Scala Extensions
[![Join the chat at https://gitter.im/reactor/reactor](https://badges.gitter.im/Join%20Chat.svg)](https://gitter.im/reactor/reactor?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)
[![Reactor Scala Extensions](https://maven-badges.herokuapp.com/maven-central/io.projectreactor/reactor-scala-extensions_2.12/badge.svg?style=plastic)](https://mvnrepository.com/artifact/io.projectreactor/reactor-scala-extensions_2.12)
[![Download](https://api.bintray.com/packages/sinwe/io.projectreactor/reactor-scala-extensions_2.12/images/download.svg) ](https://bintray.com/sinwe/io.projectreactor/reactor-scala-extensions_2.12/_latestVersion)
[![Build Status](https://travis-ci.org/reactor/reactor-scala-extensions.svg?branch=master)](https://travis-ci.org/reactor/reactor-scala-extensions)
[![codecov](https://codecov.io/gh/reactor/reactor-scala-extensions/branch/master/graph/badge.svg)](https://codecov.io/gh/reactor/reactor-scala-extensions)
                            
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

    val mono = Mono.just(1).map(_.toString)

This extension will also return scala's `scala.collection.immutable.Stream` instead of Java's `java.util.stream.Stream`
and `scala.concurrent.Future` instead of `java.util.concurrent.CompletableFuture`
## Getting it

With Gradle:
    
    repositories {
        //maven { url 'https://oss.sonatype.org/content/repositories/snapshots/' }
        mavenCentral()
    }
    
    dependencies {
<<<<<<< HEAD
        //compile "io.projectreactor:reactor-scala-extensions_2.12:0.4.0-SNAPSHOT
        //compile "io.projectreactor:reactor-scala-extensions_2.13.0-M3:0.3.5 //for scala 2.13.0-M3
        compile "io.projectreactor:reactor-scala-extensions_2.12:0.3.5 //for scala 2.12
        //compile "io.projectreactor:reactor-scala-extensions_2.11:0.3.5 //for scala 2.11
=======
        //compile "io.projectreactor:reactor-scala-extensions_2.12:0.4.1-SNAPSHOT
        //compile "io.projectreactor:reactor-scala-extensions_2.13.0-M3:0.4.0 //for scala 2.13.0-M3
        compile "io.projectreactor:reactor-scala-extensions_2.12:0.4.0 //for scala 2.12
        //compile "io.projectreactor:reactor-scala-extensions_2.11:0.4.0 //for scala 2.11
>>>>>>> master
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
<<<<<<< HEAD
        <version>0.4.0-SNAPSHOT</version>
=======
        <version>0.4.1-SNAPSHOT</version>
>>>>>>> master
    </dependency>
    -->
    <dependency>
        <groupId>io.projectreactor</groupId>
        <artifactId>reactor-scala-extensions_2.12</artifactId> <!-- for scala 2.12 -->
        <!--<artifactId>reactor-scala-extensions_2.11</artifactId> for scala 2.11 -->
        <!--<artifactId>reactor-scala-extensions_2.13.0-M3</artifactId> for scala 2.13.0-M3 -->
        <version>0.4.0</version>
    </dependency>

## Contributing
Contributions are welcome. Simply fork this project, make some modification, push and 
create a pull request.
