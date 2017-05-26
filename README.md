# Reactor Core Scala
[![Join the chat at https://gitter.im/reactor/reactor](https://badges.gitter.im/Join%20Chat.svg)](https://gitter.im/reactor/reactor?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)
[![Reactor Core Scala](https://maven-badges.herokuapp.com/maven-central/io.projectreactor/reactor-scala-extensions/badge.svg?style=plastic)](http://mvnrepository.com/artifact/io.projectreactor/reactor-scala-extensions) 
[![Build Status](https://travis-ci.org/reactor/reactor-scala-extensions.svg?branch=master)](https://travis-ci.org/reactor/reactor-scala-extensions)
[![codecov](https://codecov.io/gh/reactor/reactor-scala-extensions/branch/master/graph/badge.svg)](https://codecov.io/gh/reactor/reactor-scala-extensions)
                            
This project is a Scala wrapper for reactor-core.

This project was created after I can't find any Scala code for [reactor-core](https://github.com/reactor/reactor-core) project.
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

## Getting it

With Gradle:
    
    repositories {
        //maven { url 'https://oss.sonatype.org/content/repositories/snapshots/' }
        mavenCentral()
    }
    
    dependencies {
        //compile "io.projectreactor:reactor-scala-extensions:0.2.4-SNAPSHOT
        compile "io.projectreactor:reactor-scala-extensions:0.2.3
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
        <version>0.2.4-SNAPSHOT</version>
    </dependency>
    -->
    <dependency>
        <groupId>io.projectreactor</groupId>
        <artifactId>reactor-scala-extensions</artifactId>
        <version>0.2.3</version>
    </dependency>

## Contributing
Contributions are welcome. Simply fork this project, make some modification, push and 
create a pull request.
