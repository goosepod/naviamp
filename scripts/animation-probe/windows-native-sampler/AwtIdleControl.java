import javax.swing.*;
import java.awt.*;
public class AwtIdleControl {
  public static void main(String[] args) {
    EventQueue.invokeLater(() -> {
      JFrame frame=new JFrame("AWT idle profiling control");
      frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
      frame.add(new JLabel("Static AWT control: no audio, Compose or native animation", SwingConstants.CENTER));
      frame.setBounds(48,48,950,640);
      frame.setVisible(true);
    });
  }
}
