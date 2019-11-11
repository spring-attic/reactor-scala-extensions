Contributions are welcome. Simply fork this project, make some modification, push and create a pull request.

# Compiling and Building
This project is build using Gradle with cross-compiled on:
* Scala 2.11
* Scala 2.12
* Scala 2.13[^1]

To build:

`$ ./gradlew buildAll`

[^1]: Scala 2.13 is still compiled using 2.13.0-M3 because the general availability of scala 2.13 removes ant library used by gradle to compile scaladoc. It is supposed to be fixed on Gradle 6.x