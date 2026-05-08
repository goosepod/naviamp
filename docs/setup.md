# Local Setup

## Required Tools

- JDK 21
- Gradle wrapper, once generated
- IntelliJ IDEA or Android Studio with Kotlin Multiplatform support

The current project baseline is JDK 21 because modern Gradle and Kotlin require at least JDK 17 to run, and JDK 21 is a stable long-term support version.

## First Build

After installing JDK 21 and generating the wrapper:

```shell
./gradlew test
./gradlew :apps:desktop:run
```

## Gradle Wrapper

The wrapper is not committed yet because Gradle is not installed in the current local environment and the available Java runtime is OpenJDK 14.

Once JDK 21 and Gradle are available, generate it with:

```shell
gradle wrapper --gradle-version 8.14
```

This project currently uses Kotlin `2.2.21`, which is officially compatible with Gradle `7.6.3` through `8.14`.

