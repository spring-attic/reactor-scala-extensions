#Reactor Core Scala

[![Travis CI](https://travis-ci.org/sinwe/reactor-core-scala.svg?branch=master)](https://travis-ci.org/sinwe/reactor-core-scala)
[![codecov](https://codecov.io/gh/sinwe/reactor-core-scala/branch/master/graph/badge.svg)](https://codecov.io/gh/sinwe/reactor-core-scala)

This project is a Scala wrapper for reactor-core.

It is still in preliminary stage and requires a lot of refinement.
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

Contributions are welcome.