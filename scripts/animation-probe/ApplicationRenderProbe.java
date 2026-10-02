import java.awt.Component;
import java.awt.Container;
import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.Window;
import java.lang.management.ManagementFactory;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

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
                        .append(" visible=").append(component.isShowing()).append(" frames=").append(counter.get());
                }));
                Files.writeString(directory.resolve(phase + ".txt"), result + "\n");
                System.out.println(result);
            }
        } catch (Throwable failure) {
            failure.printStackTrace();
        }
    }

    private static void attach(Component component) {
        if (component.getClass().getName().equals("org.jetbrains.skiko.SkiaLayer") && !frames.containsKey(component)) {
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
