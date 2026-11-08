# Packaged Java accessibility snapshots

Use `PackagedAccessibilityProbe.java` only in a disposable copy of a packaged desktop application,
with an isolated synthetic profile. It invokes the unchanged app main entry point and takes a
read-only snapshot of Java accessibility contexts on the AWT event thread when requested. It does
not invoke accessibility actions, inject input, install software, enable a user-wide bridge, or
change settings. Its optional test entry point/configuration must never ship.

Compile with the same JDK as the package:

```powershell
javac -d build/accessibility-probe/classes scripts/accessibility-probe/PackagedAccessibilityProbe.java
jar cf build/accessibility-probe/probe.jar -C build/accessibility-probe/classes .
```

Preserve the canonical package and hashes. In a **copy** of its launcher configuration, set
`app.mainclass=PackagedAccessibilityProbe`, add the probe JAR to `app.classpath`, and add
`-Dnaviamp.accessibilityProbe.directory=<absolute-fresh-output-directory>` to the Java options.
Preserve all production JARs, native libraries, runtime and renderer options. Launch with disposable
APPDATA/LOCALAPPDATA on Windows and wait until its actual window paints. Write a unique
alphanumeric/hyphen/underscore snapshot name to the output directory's `snapshot` file. The
corresponding `.txt` records labels, roles, focus/enabled states, available action descriptions and
value ranges. Existing results are never overwritten. Use a fresh output directory on each launch.
Only Naviamp's visible main frames are traversed, with cycle/depth/node limits; owned dialogs and
native bridge transport are not certified by this snapshot.

On Windows, a sparse UI Automation tree does not establish missing Java accessibility semantics.
Compose uses Java Access Bridge. The package must contain `jdk.accessibility` and its bridge DLLs.
For a separate supported-client check, the installed JDK's `jaccesswalker.exe` can refresh and
expand the Java tree. If necessary, activate the bridge **only in the disposable app's configuration**
with `-Djavax.accessibility.assistive_technologies=com.sun.java.accessibility.AccessBridge`.
Do not run `jabswitch` or change the user's `.accessibility.properties` as part of this probe.
Close the official inspector and test application afterward.

Java snapshots and the external walker validate different layers; neither substitutes for spoken
screen-reader, native value-assignment, keyboard or touch acceptance. Run functional input tests
through the normal app/test workflow. Keep private snapshots local, and release observation clients
before unrelated performance intervals.

References: [Compose desktop accessibility](https://kotlinlang.org/docs/multiplatform/compose-desktop-accessibility.html),
[Java Access Bridge tools](https://docs.oracle.com/en/java/javase/21/access/enabling-and-testing-java-access-bridge.html),
[bridge architecture](https://docs.oracle.com/en/java/javase/21/access/java-access-bridge-architecture.html).
