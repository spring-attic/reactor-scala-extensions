#Reactor Core Scala
This project is a Scala wrapper for reactor-core.

It is still in preliminary stage and requires a lot of refinement.
This project was created after I can't find any Scala code for [reactor-core](https://github.com/reactor/reactor-core) project.
Using reactor-core project as it is in scala code will look ugly because
a lot of methods use Java 8 lambda which is not compatible with Scala lambda.
This will force Scala code to use anonymous class which turns ugly.

Contributions are welcome.