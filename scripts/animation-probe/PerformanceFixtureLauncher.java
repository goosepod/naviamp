import java.nio.file.Path;

/** Disposable Windows probe launcher. Never package this with Naviamp. */
public class PerformanceFixtureLauncher {
    public static void main(String[] args) throws Throwable {
        if ("yes".equals(System.getenv("NAVIAMP_590_CHILD"))) {
            try {
                if (Boolean.getBoolean("naviamp.probe.observe")) {
                    ApplicationRenderProbe.main(args);
                    return;
                }
                Class.forName("app.naviamp.desktop.MainKt")
                    .getMethod("main", String[].class).invoke(null, (Object) args);
            } catch (java.lang.reflect.InvocationTargetException exception) {
                throw exception.getCause();
            }
        } else {
            Path directory = Path.of(System.getProperty("naviamp.probe.directory"));
            ProcessBuilder child = new ProcessBuilder(System.getProperty("naviamp.probe.launcher"));
            child.environment().put("NAVIAMP_590_CHILD", "yes");
            child.environment().put("APPDATA", directory.resolve("profile/Roaming").toString());
            child.environment().put("LOCALAPPDATA", directory.resolve("profile/Local").toString());
            child.environment().remove("NAVIAMP_RASTER_FORCE_SKIA");
            child.redirectOutput(directory.resolve("stdout.log").toFile());
            child.redirectError(directory.resolve("stderr.log").toFile());
            System.exit(child.start().waitFor());
        }
    }
}
