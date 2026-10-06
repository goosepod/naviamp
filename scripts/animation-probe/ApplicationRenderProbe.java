import java.awt.Component;
import java.awt.Container;
import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.Insets;
import java.awt.image.BufferedImage;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.Window;
import java.lang.management.ManagementFactory;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.IdentityHashMap;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import javax.imageio.ImageIO;

/** Opt-in test agent: observes the actual packaged app; never creates UI or changes playback. */
public final class ApplicationRenderProbe {
    private static final Map<Component, AtomicLong> frames = new IdentityHashMap<>();
    private static final Map<Component, Object> observers = new IdentityHashMap<>();

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
                    Thread.sleep(500);
                    continue;
                }
                if (!phase.matches("[a-zA-Z0-9_-]+")) throw new IllegalArgumentException("Invalid probe phase");
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
                long compilation = ManagementFactory.getCompilationMXBean().getTotalCompilationTime();
                Thread.sleep(10_000);
                double percent = (cpu.getProcessCpuTime() - cpuBefore) * 100.0 / (System.nanoTime() - started);
                long compilationMillis = ManagementFactory.getCompilationMXBean().getTotalCompilationTime() - compilation;
                var result = new StringBuilder("APPLICATION_PROBE phase=").append(phase)
                    .append(" pid=").append(ProcessHandle.current().pid()).append(" cpu=").append(percent)
                    .append(" compilation_ms=").append(compilationMillis);
                EventQueue.invokeAndWait(() -> frames.forEach((component, counter) -> {
                    Window window = javax.swing.SwingUtilities.getWindowAncestor(component);
                    result.append(" surface=").append(window instanceof Frame ? "parent" : "owned")
                        .append(':').append(component.getWidth()).append('x').append(component.getHeight())
                        .append(" visible=").append(component.isShowing())
                        .append(" iconified=").append(window instanceof Frame && (((Frame) window).getExtendedState() & Frame.ICONIFIED) != 0)
                        .append(" frames=").append(counter.get())
                        .append(" observer_attached=").append(observerAttached(component));
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

    private static boolean capture(Path output, String name) throws Exception {
        String guard = System.getProperty("naviamp.probe.macWindowGuard");
        if (guard == null) return captureAwt(output, name);
        // Native AppKit placement can differ from AWT cached bounds after Space/window changes.
        Process inventory = new ProcessBuilder(guard, Long.toString(ProcessHandle.current().pid()), "--rectangles").start();
        if (!inventory.waitFor(3, java.util.concurrent.TimeUnit.SECONDS) || inventory.exitValue() != 0) {
            inventory.destroyForcibly();
            return false;
        }
        String bounds = new String(inventory.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        java.util.List<Rectangle> rectangles = new ArrayList<>();
        for (String line : bounds.lines().toList()) {
            String[] values = line.split(",");
            if (values.length != 4) throw new IllegalStateException("Invalid native test window bounds");
            rectangles.add(new Rectangle(Integer.parseInt(values[0]), Integer.parseInt(values[1]), Integer.parseInt(values[2]), Integer.parseInt(values[3])));
        }
        if (rectangles.isEmpty()) return false;
        Robot robot = new Robot();
        int index = 0;
        for (Rectangle rect : rectangles) {
            Insets decoration = null;
            for (Window window : Window.getWindows()) {
                if (window.isShowing() && window.getWidth() == rect.width && window.getHeight() == rect.height) {
                    decoration = window.getInsets();
                    break;
                }
            }
            if (decoration == null) return false;
            // Capture the measured client surface using actual decoration insets.
            Rectangle client = new Rectangle(rect.x + decoration.left, rect.y + decoration.top,
                rect.width - decoration.left - decoration.right, rect.height - decoration.top - decoration.bottom);
            if (client.width <= 24 || client.height <= 24) return false;
            Process validation = new ProcessBuilder(guard, Long.toString(ProcessHandle.current().pid()),
                "" + client.x, "" + client.y, "" + client.width, "" + client.height).redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
            if (!validation.waitFor(3, java.util.concurrent.TimeUnit.SECONDS) || validation.exitValue() != 0) {
                validation.destroyForcibly(); return false;
            }
            BufferedImage image = robot.createScreenCapture(client);
            // A single-color/black capture is not evidence of rendered app content.
            Set<Integer> colors = new HashSet<>();
            for (int y = 12; y < image.getHeight() - 12; y += 8)
                for (int x = 12; x < image.getWidth() - 12; x += 8) colors.add(image.getRGB(x, y));
            if (colors.size() < 16) return false;
            ImageIO.write(image, "png", output.resolve(name + (index == 0 ? "" : "-window" + index) + ".png").toFile());
            index++;
        }
        return true;
    }
    /** Physical display capture verifies moving, unobscured content, including transparent popups. */
    private static boolean captureAwt(Path directory, String name) throws Exception {
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
        if (skiaLayer && !observerAttached(component)) {
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
                    observers.put(component, observer);
                }
            } catch (ReflectiveOperationException failure) {
                throw new IllegalStateException("Cannot observe Skiko rendering", failure);
            }
        }
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) attach(child);
        }
    }

    private static boolean observerAttached(Component component) {
        Object observer = observers.get(component);
        if (observer == null) return false;
        try {
            return component.getClass().getMethod("getRenderDelegate").invoke(component) == observer;
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Cannot validate rendering observer", failure);
        }
    }
}
