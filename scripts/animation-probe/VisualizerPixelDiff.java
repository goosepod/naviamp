import java.awt.image.BufferedImage;
import java.nio.file.Path;
import javax.imageio.ImageIO;

/** Compare retained fixture captures, not live desktop pixels. Rectangles are client-relative. */
public final class VisualizerPixelDiff {
    public static void main(String[] args) throws Exception {
        if (args.length < 2 || args.length > 3) throw new IllegalArgumentException("Usage: VisualizerPixelDiff BEFORE.png AFTER.png [VISUALIZER_SIZE]");
        BufferedImage before = ImageIO.read(Path.of(args[0]).toFile());
        BufferedImage after = ImageIO.read(Path.of(args[1]).toFile());
        if (before == null || after == null || before.getWidth() != after.getWidth() || before.getHeight() != after.getHeight()) {
            throw new IllegalArgumentException("Captures must have matching dimensions");
        }
        // The 1x fixture keeps a fixed visualizer origin and an unchanging crop to its right.
        int size = args.length == 3 ? Integer.parseInt(args[2]) : 358;
        if (size <= 0) throw new IllegalArgumentException("Visualizer size must be positive");
        int changed = difference(before, after, 32, 66, size, size);
        int siblingX = size + 92;
        int sibling = difference(before, after, siblingX, 66, Math.min(510, before.getWidth() - siblingX - 40), size);
        System.out.println("visualizer_changed_pixels=" + changed + " static_sibling_changed_pixels=" + sibling);
    }

    private static int difference(BufferedImage before, BufferedImage after, int x, int y, int width, int height) {
        if (x + width > before.getWidth() || y + height > before.getHeight()) {
            throw new IllegalArgumentException("Capture does not contain the documented fixture crops");
        }
        int changed = 0;
        for (int row = y; row < y + height; row++) {
            for (int column = x; column < x + width; column++) {
                if (before.getRGB(column, row) != after.getRGB(column, row)) changed++;
            }
        }
        return changed;
    }
}
