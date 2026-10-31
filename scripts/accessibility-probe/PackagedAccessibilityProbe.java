import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.Window;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import javax.accessibility.Accessible;
import javax.accessibility.AccessibleAction;
import javax.accessibility.AccessibleContext;
import javax.accessibility.AccessibleValue;

/** Read-only test entry point for a disposable package; never changes settings or invokes actions. */
public final class PackagedAccessibilityProbe {
    public static void main(String[] args) throws Throwable {
        Path output = Path.of(System.getProperty("naviamp.accessibilityProbe.directory"));
        Files.createDirectories(output);
        Thread observer = new Thread(() -> {
            try {
                Files.writeString(output.resolve("pid.txt"), Long.toString(ProcessHandle.current().pid()),
                    StandardOpenOption.CREATE_NEW);
                String previous = "";
                while (true) {
                    Path command = output.resolve("snapshot");
                    String name = Files.exists(command) ? Files.readString(command).trim() : "";
                    if (!name.isEmpty() && !name.equals(previous)) {
                        if (!name.matches("[a-zA-Z0-9_-]+")) throw new IllegalArgumentException("Invalid snapshot name");
                        previous = name;
                        StringBuilder result = new StringBuilder("pid=").append(ProcessHandle.current().pid())
                            .append(" bridge_module=").append(ModuleLayer.boot().findModule("jdk.accessibility").isPresent())
                            .append(" assistive_technologies=")
                            .append(System.getProperty("javax.accessibility.assistive_technologies", "unset")).append('\n');
                        EventQueue.invokeAndWait(() -> {
                            for (Window window : Window.getWindows()) {
                                if (window.isShowing() && window instanceof Frame frame && "Naviamp".equals(frame.getTitle())) {
                                    dump(window.getAccessibleContext(), "root", result,
                                        Collections.newSetFromMap(new IdentityHashMap<>()), new int[]{0});
                                }
                            }
                        });
                        Files.writeString(output.resolve(name + ".txt"), result, StandardOpenOption.CREATE_NEW);
                    }
                    Thread.sleep(500);
                }
            } catch (Throwable failure) { failure.printStackTrace(); }
        }, "Naviamp read-only accessibility probe");
        observer.setDaemon(true);
        observer.start();
        try {
            Class.forName("app.naviamp.desktop.MainKt").getMethod("main", String[].class)
                .invoke(null, (Object) args);
        } catch (InvocationTargetException failure) { throw failure.getCause(); }
    }

    private static void dump(AccessibleContext context, String path, StringBuilder result,
                             Set<AccessibleContext> seen, int[] count) {
        if (context == null) return;
        if (!seen.add(context)) throw new IllegalStateException("Accessibility cycle at " + path);
        if (++count[0] > 10_000 || path.length() > 500) throw new IllegalStateException("Unbounded accessibility tree");
        result.append(path).append(" role=").append(context.getAccessibleRole())
            .append(" name=").append(context.getAccessibleName())
            .append(" description=").append(context.getAccessibleDescription())
            .append(" states=").append(context.getAccessibleStateSet());
        AccessibleAction action = context.getAccessibleAction();
        if (action != null) for (int i = 0; i < action.getAccessibleActionCount(); i++) {
            result.append(" action[").append(i).append("]=").append(action.getAccessibleActionDescription(i));
        }
        AccessibleValue value = context.getAccessibleValue();
        if (value != null) result.append(" value=").append(value.getCurrentAccessibleValue())
            .append(" range=").append(value.getMinimumAccessibleValue()).append("..").append(value.getMaximumAccessibleValue());
        result.append('\n');
        for (int i = 0; i < context.getAccessibleChildrenCount(); i++) {
            Accessible child = context.getAccessibleChild(i);
            if (child != null) dump(child.getAccessibleContext(), path + "/" + i, result, seen, count);
        }
        seen.remove(context);
    }
}
