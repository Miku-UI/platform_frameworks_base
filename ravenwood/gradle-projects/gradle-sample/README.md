# Ravenwood Gradle Sample

This directory contains a sample gradle project to run host tests on
Ravenwood.

We can't build ravenwood-runtime with gradle, so the runtime must be built
separately with `m ravenwood` before running the test with gradle.

## `:app:testDebugUnitTest` Runs Ravenwood By Default

To run this test on Ravenwood, either just use the right-click menu, or run this:
```bash
./gradlew :app:testDebugUnitTest --info --rerun
```

To run it on Robolectric, use this:

```bash
./gradlew :app:testDebugUnitTest --info --rerun -PenableRavenwood=false
```

## Using Ravenwood And Robolectric For Different Sub-projects

This project contains a sub-project `TestLibrary`, which uses Robolecric.

To run this test, either just use the right-click menu, or use this:
```bash
./gradlew :TestLibrary:testDebugUnitTest --info --rerun
```
