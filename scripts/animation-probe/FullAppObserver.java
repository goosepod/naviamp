import java.awt.*;
import java.awt.image.BufferedImage;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import javax.accessibility.*;
import javax.imageio.ImageIO;

/** Test-only observer for an unmodified packaged app. Never reads the user's profile. */
public final class FullAppObserver {
    /** Observer-free calibration: this separate JVM captures only the disposable app rectangle. */
    public static void main(String[] args) throws Exception {
        if (args.length != 8) throw new IllegalArgumentException("pid phase seconds output x y width height");
        ProcessHandle app = ProcessHandle.of(Long.parseLong(args[0])).orElseThrow();
        String phase = args[1];
        int seconds = Integer.parseInt(args[2]);
        Path directory = Path.of(args[3]);
        Files.createDirectories(directory);
        Rectangle rectangle = new Rectangle(Integer.parseInt(args[4]), Integer.parseInt(args[5]),
            Integer.parseInt(args[6]), Integer.parseInt(args[7]));
        Robot robot = new Robot();
        robot.mouseMove(rectangle.x + rectangle.width - 10, rectangle.y + rectangle.height - 10);
        Thread.sleep(5000);
        BufferedImage before = robot.createScreenCapture(rectangle);
        requireVisible(before);
        List<ProcessHandle> compositors = ProcessHandle.allProcesses().filter(p ->
            Set.of("xfwm4", "Xorg").contains(Path.of(p.info().command().orElse("unknown")).getFileName().toString())).toList();
        Map<ProcessHandle, Long> previous = new HashMap<>();
        for (ProcessHandle process : compositors) previous.put(process, cpu(process));
        long startCpu = cpu(app), startTime = System.nanoTime();
        Thread.sleep(seconds * 1000L);
        long elapsed = System.nanoTime() - startTime;
        long endCpu = cpu(app);
        if (!app.isAlive() || startCpu < 0 || endCpu < startCpu) throw new IllegalStateException("App exited during calibration");
        BufferedImage after = robot.createScreenCapture(rectangle);
        requireVisible(after);
        int textChanges = changed(before, after, new Rectangle(15, 451, 302, 18));
        int waveformChanges = changed(before, after, new Rectangle(63, 407, 206, 28));
        int siblingChanges = changed(before, after, new Rectangle(50, 130, 150, 150));
        String result = "FULL_APP_EXTERNAL phase=" + phase + " pid=" + app.pid() + " seconds=" + seconds +
            " cpu_percent=" + (endCpu - startCpu) * 100.0 / elapsed + " parent_frames=unobserved" +
            " text_pixels_changed=" + textChanges + " waveform_pixels_changed=" + waveformChanges +
            " sibling_pixels_changed=" + siblingChanges;
        for (ProcessHandle process : compositors) if (previous.get(process) >= 0)
            result += " compositor_" + process.pid() + "_cpu_percent=" + (cpu(process) - previous.get(process)) * 100.0 / elapsed;
        Files.writeString(directory.resolve(phase + "-metrics.txt"), result + "\n", StandardOpenOption.CREATE_NEW);
        ImageIO.write(before, "png", directory.resolve(phase + "-before.png").toFile());
        ImageIO.write(after, "png", directory.resolve(phase + "-after.png").toFile());
        System.out.println(result);
        if (textChanges < 20 || waveformChanges < 10 || siblingChanges != 0)
            throw new IllegalStateException("Calibration animation/unchanged artwork assertion failed");
    }

    private static native Object[] instances(Class<?> type);
    private static native void traceRedraw(boolean enabled);
    private static final AtomicLong frames = new AtomicLong();
    private static final Set<Component> observed = Collections.newSetFromMap(new IdentityHashMap<>());
    private static Path output;
    private static final long startedNanos = System.nanoTime();

    public static void start(String directory) {
        output = Path.of(directory);
        Thread observer = new Thread(() -> {
            try {
                Files.createDirectories(output);
                // A fresh directory keeps commands, measurements, and image provenance immutable.
                Files.writeString(output.resolve("pid.txt"), Long.toString(ProcessHandle.current().pid()), StandardOpenOption.CREATE_NEW);
                Thread.sleep(5000); // Let the application configure and create its own AWT host first.
                int consumed = 0;
                while (true) {
                    EventQueue.invokeAndWait(() -> {
                        for (Window w : Window.getWindows()) if ("Naviamp".equals(w.getName()) ||
                            w instanceof Frame && "Naviamp".equals(((Frame) w).getTitle())) install(w);
                    });
                    Path commands = output.resolve("commands.txt");
                    List<String> lines = Files.exists(commands) ? Files.readAllLines(commands) : List.of();
                    while (consumed < lines.size()) {
                        String[] command = lines.get(consumed++).split("\t");
                        if (Files.exists(output.resolve(command[0] + ".reply"))) continue;
                        String result;
                        try { result = execute(command); }
                        catch (Throwable failure) { result = "ERROR " + failure; failure.printStackTrace(); }
                        Path temporary = output.resolve(command[0] + ".tmp");
                        Files.writeString(temporary, result + "\n");
                        Files.move(temporary, output.resolve(command[0] + ".reply"), StandardCopyOption.REPLACE_EXISTING);
                    }
                    Thread.sleep(1000);
                }
            } catch (Throwable failure) { failure.printStackTrace(); }
        }, "full-app-observer");
        observer.setDaemon(true);
        observer.start();
    }

    private static void install(Component component) {
        boolean skia = false;
        for (Class<?> type = component.getClass(); type != null; type = type.getSuperclass())
            if (type.getName().equals("org.jetbrains.skiko.SkiaLayer")) skia = true;
        if (skia && !observed.contains(component)) {
            try {
                Class<?> type = Class.forName("org.jetbrains.skiko.SkikoRenderDelegate", true, component.getClass().getClassLoader());
                Object original = component.getClass().getMethod("getRenderDelegate").invoke(component);
                if (original == null) return;
                Object proxy = Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (unused, method, args) -> {
                    if (method.getName().equals("onRender")) frames.incrementAndGet();
                    try { return method.invoke(original, args); }
                    catch (InvocationTargetException failure) { throw failure.getCause(); }
                });
                component.getClass().getMethod("setRenderDelegate", type).invoke(component, proxy);
                observed.add(component);
                System.out.println("FULL_APP_OBSERVER layer=" + component.getClass().getMethod("getRenderApi").invoke(component));
            } catch (ReflectiveOperationException failure) { throw new RuntimeException(failure); }
        }
        if (component instanceof Container) for (Component child : ((Container) component).getComponents()) install(child);
    }

    private static Window window() {
        return Arrays.stream(Window.getWindows()).filter(w -> w instanceof Frame &&
            "Naviamp".equals(((Frame) w).getTitle()) && w.isDisplayable()).findFirst().orElseThrow();
    }

    private static String execute(String[] command) throws Exception {
        if (command[1].equals("bridge")) {
            Class<?> bridge = Class.forName("org.GNOME.Accessibility.AtkWrapper");
            bridge.getConstructor().newInstance();
            Field enabled = bridge.getDeclaredField("accessibilityEnabled"); enabled.setAccessible(true);
            EventQueue.invokeAndWait(() -> {
                try {
                    bridge.getMethod("loadAtkBridge").invoke(null);
                    bridge.getMethod("windowOpen", AccessibleContext.class, boolean.class)
                        .invoke(null, window().getAccessibleContext(), true);
                }
                catch (ReflectiveOperationException failure) { throw new RuntimeException(failure); }
            });
            return "ATK_BRIDGE initialized enabled=" + enabled.getBoolean(null);
        }
        if (command[1].equals("trace")) {
            traceRedraw(true);
            try { Thread.sleep(5000); } finally { traceRedraw(false); }
            return "REDRAW_TRACE captured in app.log; diagnostic only, excluded from measurements";
        }
        if (command[1].equals("measure")) return measure(command[2], Integer.parseInt(command[3]));
        if (command[1].equals("state")) {
            Object[] owners = instances(Class.forName("app.naviamp.presentation.NaviampCore"));
            if (owners == null || owners.length != 1) throw new IllegalStateException("Expected one isolated Core owner");
            Object flow = owners[0].getClass().getMethod("getState").invoke(owners[0]);
            Method getValue = flow.getClass().getMethod("getValue");
            getValue.setAccessible(true);
            Object before = getValue.invoke(flow);
            Thread.sleep(3000);
            Object after = getValue.invoke(flow);
            StringBuilder changes = new StringBuilder();
            diff(before, after, "core", changes, 0);
            return changes.isEmpty() ? "CORE_STATE unchanged" : changes.toString();
        }
        StringBuilder result = new StringBuilder();
        EventQueue.invokeAndWait(() -> {
            Window w = window();
            switch (command[1]) {
                case "dump":
                    dump(w.getAccessibleContext(), "root", result, 0);
                    for (Window owned : w.getOwnedWindows()) if (owned.isShowing()) result.append("OWNED name=")
                        .append(owned.getName()).append(" focusable=").append(owned.isFocusableWindow()).append('\n');
                    break;
                case "action":
                    AccessibleContext action = find(w.getAccessibleContext(), command[2]);
                    if (action == null || action.getAccessibleAction() == null) throw new IllegalStateException("No action: " + command[2]);
                    result.append("ACTION ").append(command[2]).append(' ')
                        .append(action.getAccessibleAction().doAccessibleAction(command.length > 3 ? Integer.parseInt(command[3]) : 0));
                    break;
                case "focus":
                    AccessibleContext focus = find(w.getAccessibleContext(), command[2]);
                    if (focus == null) throw new IllegalStateException("No focus target: " + command[2]);
                    focus.getAccessibleComponent().requestFocus();
                    result.append("FOCUS requested ").append(command[2]);
                    break;
                case "minimize": ((Frame) w).setExtendedState(Frame.ICONIFIED); result.append("minimized"); break;
                case "restore": ((Frame) w).setExtendedState(Frame.NORMAL); w.toFront(); result.append("restored"); break;
                case "resize": w.setBounds(60, 40, 1040, 760); result.append("resized"); break;
                default: throw new IllegalArgumentException(command[1]);
            }
        });
        return result.toString();
    }

    private static AccessibleContext find(AccessibleContext context, String name) {
        if (name.equals(context.getAccessibleName())) return context;
        for (int i = 0; i < context.getAccessibleChildrenCount(); i++) {
            Accessible child = context.getAccessibleChild(i);
            if (child == null) continue;
            AccessibleContext found = find(child.getAccessibleContext(), name);
            if (found != null) return found;
        }
        return null;
    }

    private static void dump(AccessibleContext context, String path, StringBuilder result, int depth) {
        if (depth > 40) throw new IllegalStateException("Accessibility tree cycle");
        result.append(path).append(" role=").append(context.getAccessibleRole()).append(" name=")
            .append(context.getAccessibleName()).append(" description=").append(context.getAccessibleDescription())
            .append(" states=").append(context.getAccessibleStateSet());
        AccessibleComponent component = context.getAccessibleComponent();
        if (component != null) result.append(" bounds=").append(component.getBounds());
        AccessibleAction actions = context.getAccessibleAction();
        if (actions != null) for (int i = 0; i < actions.getAccessibleActionCount(); i++) result.append(" action[")
            .append(i).append("]=").append(actions.getAccessibleActionDescription(i));
        AccessibleValue value = context.getAccessibleValue();
        if (value != null) result.append(" value=").append(value.getCurrentAccessibleValue());
        result.append('\n');
        for (int i = 0; i < context.getAccessibleChildrenCount(); i++) {
            Accessible child = context.getAccessibleChild(i);
            if (child != null) dump(child.getAccessibleContext(), path + "/" + i, result, depth + 1);
        }
    }

    private static long cpu(ProcessHandle process) {
        return process.info().totalCpuDuration().map(java.time.Duration::toNanos).orElse(-1L);
    }

    private static String measure(String phase, int seconds) throws Exception {
        if (observed.isEmpty()) throw new IllegalStateException("No parent rendering delegate observed");
        final Rectangle[] rectangle = new Rectangle[1];
        final String[] state = new String[1];
        final boolean[] hidden = new boolean[1];
        final Rectangle[] regions = new Rectangle[2];
        EventQueue.invokeAndWait(() -> {
            Window w = window(); rectangle[0] = w.getBounds();
            hidden[0] = (((Frame) w).getExtendedState() & Frame.ICONIFIED) != 0;
            if (!hidden[0] && Boolean.getBoolean("naviamp.probe.skipAccessibility")) {
                try {
                    List<String> rectangles = Files.readAllLines(output.resolve("regions.txt"));
                    for (int i = 0; i < 2; i++) {
                        int[] parts = Arrays.stream(rectangles.get(i).split(" ")).mapToInt(Integer::parseInt).toArray();
                        regions[i] = new Rectangle(parts[0], parts[1], parts[2], parts[3]);
                    }
                } catch (Exception failure) { throw new RuntimeException(failure); }
            } else if (!hidden[0]) {
                AccessibleContext title = find(w.getAccessibleContext(), "Long scrolling player title with enough metadata to overflow its viewport");
                if (title == null) throw new IllegalStateException("Fixture title is absent");
                AccessibleComponent text = title.getAccessibleComponent();
                Point location = text.getLocationOnScreen();
                regions[0] = new Rectangle(location.x - rectangle[0].x, location.y - rectangle[0].y,
                    text.getBounds().width, text.getBounds().height);
                // The progress slider is discovered from the real platform accessibility tree.
                AccessibleContext slider = firstSlider(w.getAccessibleContext());
                if (slider == null) throw new IllegalStateException("Fixture progress slider is absent");
                AccessibleComponent progress = slider.getAccessibleComponent();
                Point progressLocation = progress.getLocationOnScreen();
                regions[1] = new Rectangle(progressLocation.x - rectangle[0].x, progressLocation.y - rectangle[0].y,
                    progress.getBounds().width, progress.getBounds().height);
            }
            state[0] = "minimized=" + ((((Frame) w).getExtendedState() & Frame.ICONIFIED) != 0) + " visibleOwned=" +
                Arrays.stream(w.getOwnedWindows()).filter(Window::isShowing).count();
        });
        if (!hidden[0]) {
            // Match the component probe: do not accidentally benchmark a hovered tooltip.
            new Robot().mouseMove(rectangle[0].x + rectangle[0].width - 10, rectangle[0].y + rectangle[0].height - 10);
        }
        Thread.sleep(5000);
        EventQueue.invokeAndWait(() -> {
            Window w = window();
            state[0] = "minimized=" + ((((Frame) w).getExtendedState() & Frame.ICONIFIED) != 0) + " visibleOwned=" +
                Arrays.stream(w.getOwnedWindows()).filter(Window::isShowing).count();
        });
        BufferedImage before = hidden[0] ? null : new Robot().createScreenCapture(rectangle[0]);
        if (!hidden[0]) requireVisible(before);
        List<ProcessHandle> compositors = ProcessHandle.allProcesses().filter(p ->
            Set.of("xfwm4", "Xorg").contains(Path.of(p.info().command().orElse("unknown")).getFileName().toString())).toList();
        Map<ProcessHandle, Long> previous = new HashMap<>();
        for (ProcessHandle process : compositors) previous.put(process, cpu(process));
        long startFrames = frames.get(), startCpu = cpu(ProcessHandle.current()), startTime = System.nanoTime();
        long startCompiler = compilerTicks();
        Thread.sleep(seconds * 1000L);
        long elapsed = System.nanoTime() - startTime;
        long endCpu = cpu(ProcessHandle.current()), endFrames = frames.get();
        StringBuilder result = new StringBuilder("FULL_APP phase=" + phase + " seconds=" + seconds + " cpu_percent=" +
            (endCpu - startCpu) * 100.0 / elapsed + " parent_frames=" + (endFrames - startFrames) + " " + state[0] +
            " start_uptime_s=" + (startTime - startedNanos) / 1e9 + " compiler_cpu_ms=" +
            (compilerTicks() - startCompiler) * 1000.0 / Long.getLong("naviamp.probe.clockTicks", 100L));
        for (ProcessHandle process : compositors) if (previous.get(process) >= 0) result.append(" compositor_")
            .append(process.pid()).append("_cpu_percent=").append((cpu(process) - previous.get(process)) * 100.0 / elapsed);
        BufferedImage after = hidden[0] ? null : new Robot().createScreenCapture(rectangle[0]);
        if (!hidden[0]) {
            requireVisible(after);
            int textChanges = changed(before, after, regions[0]);
            int waveformChanges = changed(before, after, regions[1]);
            int siblingChanges = changed(before, after, new Rectangle(50, 130, 150, 150));
            result.append(" text_pixels_changed=").append(textChanges).append(" waveform_pixels_changed=")
                .append(waveformChanges).append(" sibling_pixels_changed=").append(siblingChanges);
            Files.writeString(output.resolve(phase + "-metrics.txt"), result + "\n");
            ImageIO.write(before, "png", output.resolve(phase + "-before.png").toFile());
            ImageIO.write(after, "png", output.resolve(phase + "-after.png").toFile());
            if (siblingChanges != 0) throw new IllegalStateException("Unrelated artwork pixels changed");
            if (phase.startsWith("marquee") || phase.startsWith("combined") || phase.startsWith("restored") || phase.startsWith("resized"))
                if (textChanges < 20) throw new IllegalStateException("Text froze");
            if (phase.startsWith("waveform") || phase.startsWith("combined") || phase.startsWith("restored") || phase.startsWith("resized"))
                if (waveformChanges < 10) throw new IllegalStateException("Progress froze");
            if (phase.startsWith("static"))
                if (textChanges != 0 || waveformChanges != 0) throw new IllegalStateException("Static player moved");
            if (phase.startsWith("paused") && waveformChanges != 0) throw new IllegalStateException("Paused progress moved");
        }
        if (!hidden[0]) {
            ImageIO.write(before, "png", output.resolve(phase + "-before.png").toFile());
            ImageIO.write(after, "png", output.resolve(phase + "-after.png").toFile());
        } else if (endFrames != startFrames || !state[0].endsWith("visibleOwned=0")) {
            throw new IllegalStateException("Minimized app kept rendering or showing native raster windows: " + result);
        }
        return result.toString();
    }

    private static AccessibleContext firstSlider(AccessibleContext context) {
        if (context.getAccessibleRole() == AccessibleRole.SLIDER) return context;
        for (int i = 0; i < context.getAccessibleChildrenCount(); i++) {
            Accessible child = context.getAccessibleChild(i);
            if (child == null) continue;
            AccessibleContext slider = firstSlider(child.getAccessibleContext());
            if (slider != null) return slider;
        }
        return null;
    }

    private static void requireVisible(BufferedImage image) {
        int nonblack = 0;
        for (int y = 0; y < image.getHeight(); y += 4) for (int x = 0; x < image.getWidth(); x += 4)
            if ((image.getRGB(x, y) & 0xffffff) != 0) nonblack++;
        if (nonblack < image.getWidth() * image.getHeight() / 32) throw new IllegalStateException("Blank or obscured window");
        // These points lie in the fixture's solid blue-gray album placeholder. A merely
        // nonblack capture can be a covering editor window, so require the fixture's pixels.
        for (Point point : List.of(new Point(100, 180), new Point(150, 180), new Point(50, 130))) {
            int art = image.getRGB(point.x, point.y);
            if (Math.abs((art >> 16 & 255) - 67) > 2 || Math.abs((art >> 8 & 255) - 83) > 2 ||
                Math.abs((art & 255) - 107) > 2)
                throw new IllegalStateException("Fixture artwork is blank or covered");
        }
    }

    private static int changed(BufferedImage before, BufferedImage after, Rectangle bounds) {
        int changes = 0;
        for (int y = bounds.y; y < bounds.y + bounds.height; y++) for (int x = bounds.x; x < bounds.x + bounds.width; x++)
            if (before.getRGB(x, y) != after.getRGB(x, y)) changes++;
        return changes;
    }

    private static long compilerTicks() throws Exception {
        long ticks = 0;
        try (var tasks = Files.list(Path.of("/proc/self/task"))) {
            for (Path task : tasks.toList()) {
                try {
                    String name = Files.readString(task.resolve("comm"));
                    if (!name.startsWith("C1 Compiler") && !name.startsWith("C2 Compiler")) continue;
                    String stat = Files.readString(task.resolve("stat"));
                    String[] fields = stat.substring(stat.lastIndexOf(')') + 1).strip().split("\\s+");
                    ticks += Long.parseLong(fields[11]) + Long.parseLong(fields[12]);
                } catch (NoSuchFileException ignored) { /* compiler thread exited */ }
            }
        }
        return ticks;
    }

    private static void diff(Object before, Object after, String path, StringBuilder result, int depth) throws Exception {
        if (Objects.equals(before, after)) return;
        if (before == null || after == null || depth > 7 || !before.getClass().equals(after.getClass())) {
            result.append(path).append(" changed\n"); return;
        }
        if (before instanceof Number || before instanceof Boolean) {
            result.append(path).append(' ').append(before).append(" -> ").append(after).append('\n'); return;
        }
        if (before instanceof String || before instanceof Enum<?>) {
            result.append(path).append(" changed\n"); return; // Never dump credentials or other string values.
        }
        if (before instanceof List<?>) {
            List<?> a = (List<?>) before, b = (List<?>) after;
            if (a.size() != b.size()) result.append(path).append(" size changed\n");
            else for (int i = 0; i < a.size(); i++) diff(a.get(i), b.get(i), path + "[" + i + "]", result, depth+1);
            return;
        }
        for (Field field : before.getClass().getDeclaredFields()) if (!Modifier.isStatic(field.getModifiers())) {
            field.setAccessible(true);
            diff(field.get(before), field.get(after), path + "." + field.getName(), result, depth+1);
        }
    }
}
