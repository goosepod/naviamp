import java.awt.Component;
import java.awt.Container;
import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.Window;
import java.lang.management.ManagementFactory;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import javax.imageio.ImageIO;

/** Opt-in test agent: observes the actual packaged app; never creates UI or changes playback. */
public final class ApplicationRenderProbe {
    private static final Map<Component, AtomicLong> frames = new IdentityHashMap<>();

    public static void premain(String directory) {
        Thread worker = new Thread(() -> run(Path.of(directory)), "Naviamp application rendering probe");
        worker.setDaemon(true);
        worker.start();
    }

    private static void run(Path directory) {
        try {
            Files.createDirectories(directory);
            var cpu = (com.sun.management.OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
            String previous = "";
            while (true) {
                Path command = directory.resolve("phase");
                String phase = Files.exists(command) ? Files.readString(command).trim() : "";
                if (phase.isEmpty() || phase.equals(previous)) {
                    Thread.sleep(100);
                    continue;
                }
                previous = phase;
                EventQueue.invokeAndWait(() -> {
                    for (Window window : Window.getWindows()) {
                        if (window.isShowing()) attach(window);
                    }
                    frames.values().forEach(counter -> counter.set(0));
                });
                boolean beforeVisible = capture(directory, phase + "-before");
                long cpuBefore = cpu.getProcessCpuTime();
                long started = System.nanoTime();
                Thread.sleep(10_000);
                double percent = (cpu.getProcessCpuTime() - cpuBefore) * 100.0 / (System.nanoTime() - started);
                var result = new StringBuilder("APPLICATION_PROBE phase=").append(phase)
                    .append(" pid=").append(ProcessHandle.current().pid()).append(" cpu=").append(percent);
                EventQueue.invokeAndWait(() -> frames.forEach((component, counter) -> {
                    Window window = javax.swing.SwingUtilities.getWindowAncestor(component);
                    result.append(" surface=").append(window instanceof Frame ? "parent" : "owned")
                        .append(':').append(component.getWidth()).append('x').append(component.getHeight())
                        .append(" visible=").append(component.isShowing())
                        .append(" iconified=").append(window instanceof Frame && (((Frame) window).getExtendedState() & Frame.ICONIFIED) != 0)
                        .append(" frames=").append(counter.get());
                }));
                boolean afterVisible = capture(directory, phase + "-after");
                result.append(" physical_captures=").append(beforeVisible).append('/').append(afterVisible);
                Files.writeString(directory.resolve(phase + ".txt"), result + "\n");
                System.out.println(result);
            }
        } catch (Throwable failure) {
            failure.printStackTrace();
        }
    }

    /** Physical display capture verifies moving, unobscured content, including transparent popups. */
    private static boolean capture(Path directory, String name) throws Exception {
        Rectangle[] bounds = new Rectangle[1];
        boolean[] active = new boolean[1];
        EventQueue.invokeAndWait(() -> {
            for (Window window : Window.getWindows()) {
                if (window.isShowing() && !(window instanceof Frame && (((Frame) window).getExtendedState() & Frame.ICONIFIED) != 0)) {
                    bounds[0] = bounds[0] == null ? window.getBounds() : bounds[0].union(window.getBounds());
                    active[0] |= window.isActive();
                }
            }
        });
        // An inactive app can be on a different desktop space. Never save that desktop's content.
        if (bounds[0] == null || !active[0]) return false;
        ImageIO.write(new Robot().createScreenCapture(bounds[0]), "png",
            directory.resolve(name + ".png").toFile());
        return true;
    }

    private static void attach(Component component) {
        boolean skiaLayer = false;
        for (Class<?> type = component.getClass(); type != null; type = type.getSuperclass()) {
            if (type.getName().equals("org.jetbrains.skiko.SkiaLayer")) skiaLayer = true;
        }
        if (skiaLayer && !frames.containsKey(component)) {
            try {
                Class<?> type = component.getClass();
                Class<?> api = Class.forName("org.jetbrains.skiko.SkikoRenderDelegate", true, type.getClassLoader());
                Object original = type.getMethod("getRenderDelegate").invoke(component);
                if (original != null) {
                    AtomicLong counter = new AtomicLong();
                    Object observer = Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{api},
                        (proxy, method, arguments) -> {
                            if (method.getName().equals("onRender")) counter.incrementAndGet();
                            try { return method.invoke(original, arguments); }
                            catch (InvocationTargetException failure) { throw failure.getCause(); }
                        });
                    type.getMethod("setRenderDelegate", api).invoke(component, observer);
                    frames.put(component, counter);
                }
            } catch (ReflectiveOperationException failure) {
                throw new IllegalStateException("Cannot observe Skiko rendering", failure);
            }
        }
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) attach(child);
        }
    }
}
