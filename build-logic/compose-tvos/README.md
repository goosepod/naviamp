# Naviamp's pinned Compose tvOS plugin experiment (#590)

Source: https://github.com/sajidalidev/compose-tvos
Upstream commit: `f1b260751bac8b073519e65675fe2797dab71b8a` (1.4.2).
Apache-2.0 license is retained in LICENSE. This local build is not published under the upstream
coordinates. The minimal build replaces upstream publishing/TestKit packaging; upstream main
sources are retained with the changes described below.

Naviamp changes: scope empty-variant strict diagnostics to module coordinates selected by resolved
tvOS configurations, rather than every component-metadata candidate seen in any platform graph.
Unmapped fork-edge errors remain strict. Selected genuine gaps remain errors. Added regressions
cover non-tvOS/discarded candidates and selected gaps. Configuration-cache acceptance remains open;
these experiments run without it. No relaxation of failed Gradle resolution is introduced.

Safe defaults: no remote manifest (`manifestUrl` is empty), `strictMode` on, and no Maven Local or
JetBrains development repository fallback; a consumer without configured repositories fails.

Consumer experiment: add this directory as pluginManagement.includeBuild, retain
composeTvos.strictMode=true, disable remote manifest fetching and pin the Compose plugin version.
Test with the app's Gradle wrapper: `./gradlew -p build-logic/compose-tvos test`.
