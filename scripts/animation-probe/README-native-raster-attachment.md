# Native raster attachment isolation

`NativeRasterAttachmentProbe.java` is a disposable Windows boundary test for issue #590. It
creates a plain Skiko OpenGL parent and invokes the packaged app's actual DirectComposition JNI
adapter. It never opens the app profile, starts playback, installs an input surface over the
application or captures the screen. Its colors and keyboard controls are test instrumentation.

Use an existing packaged app copied into a new disposable directory. Keep its original jars,
native resources and runtime together. Compile against that copy's `app/*` classpath with JDK 21,
then jar **all** generated classes (including anonymous listener/delegate classes). Add the probe
jar to the copied launcher's classpath and change only that copy's `app.mainclass` to
`NativeRasterAttachmentProbe`. Preserve the original launcher configuration separately.

Set these JVM properties in the disposable launch configuration:

```text
-Dskiko.renderApi=OPENGL
-Dnaviamp.probe.directory=C:/absolute/path/to/fresh-evidence-directory
-Dnaviamp.probe.label=candidate
```

Keep `compose.application.resources.dir` and `skiko.library.path` pointed at the copied package's
original locations. The probe loads `playback/bass/windows-x64/naviamp_visualizer_opengl.dll`
from that resources directory and accesses the test subject's internal JNI bridge reflectively.
It intentionally fails if that bridge's signature changes; never stub the real boundary to pass.

Default scenario: attach the magenta native region during the first parent recording callback.
Expected settled output is a blue parent with a magenta 200×30 region at (50,50). Parent recording
callbacks and native attachment/commit returns are logged to `events.log`. They do **not** prove
GPU/compositor completion. Close and relaunch with fresh evidence for repeated startup trials.

Settled-attachment scenario: add `-Dnaviamp.probe.skipNative=true`, inspect the untouched blue
window, then press **A** in that window. This attaches the native region without explicitly
requesting a parent frame. Inspect again after native commit. Other keys explicitly request a
parent redraw and therefore must not be used before accepting the untouched result. Close the
window normally to release the native region, Skiko layer and process. A deferred scheduling
variant (`-Dnaviamp.probe.defer=true`) is exploratory and is not a production workaround.

Compare baseline and candidate at identical window/display conditions. Independently verify the
physical display when per-window capture disagrees or appears blank. A passing small probe does
not establish application startup or animation-budget acceptance. Retain failures and correlate
with the full app rather than changing its renderer, frame cadence or redraw policy from this
probe alone.
