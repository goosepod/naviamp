import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.lang.reflect.*;
import java.nio.file.*;
import javax.imageio.ImageIO;
import javax.swing.*;
import org.jetbrains.skiko.*;

// Disposable native interoperability experiment. No application profile or product behavior.
public class NativeRasterAttachmentProbe {
 static PrintWriter log;
 static long start=System.nanoTime(), handle;
 static Object bridge;
 static Method create,present,close;
 static void event(String message) { log.printf("%.3f %s%n",(System.nanoTime()-start)/1e6,message);log.flush(); }
 static SkiaLayer layer;
 static byte[] png;
 static void attach() {
  try {
   event("attach showing="+layer.isShowing()+" hwnd="+layer.getWindowHandle());
   if(handle==0)handle=(long)create.invoke(bridge,layer.getCanvas());
   present.invoke(bridge,handle,new double[]{50,50,200,30,0,0,200,30,0},new byte[][]{png},
    new double[][]{{0,0,0,200,30}},new double[][]{{},{},{}},new double[][]{{},{},{}},new boolean[]{false,false,false},true);
   event("commit returned handle="+handle);
  } catch(Throwable e){e.printStackTrace(log);log.flush();}
 }
 public static void main(String[] args)throws Exception {
  Path dir=Path.of(System.getProperty("naviamp.probe.directory"));Files.createDirectories(dir);
  log=new PrintWriter(Files.newBufferedWriter(dir.resolve("events.log")));
  System.load(Path.of(System.getProperty("compose.application.resources.dir"),"playback/bass/windows-x64/naviamp_visualizer_opengl.dll").toString());
  Class<?> type=Class.forName("app.naviamp.ui.WindowsRasterNative");Field instance=type.getDeclaredField("INSTANCE");instance.setAccessible(true);bridge=instance.get(null);
  create=type.getDeclaredMethod("create",Component.class);create.setAccessible(true);
  present=type.getDeclaredMethod("present",long.class,double[].class,byte[][].class,double[][].class,double[][].class,double[][].class,boolean[].class,boolean.class);present.setAccessible(true);
  close=type.getDeclaredMethod("close",long.class);close.setAccessible(true);
  BufferedImage image=new BufferedImage(200,30,BufferedImage.TYPE_INT_ARGB);Graphics2D g=image.createGraphics();g.setColor(Color.MAGENTA);g.fillRect(0,0,200,30);g.dispose();ByteArrayOutputStream bytes=new ByteArrayOutputStream();ImageIO.write(image,"png",bytes);png=bytes.toByteArray();
  EventQueue.invokeLater(()->{
   try {
    for(Constructor<?> c:SkiaLayer.class.getConstructors())if(c.getParameterCount()==7 && c.getParameterTypes()[1]==SkiaLayerProperties.class) layer=(SkiaLayer)c.newInstance(null,new SkiaLayerProperties(),null,null,null,29,null);
    if(layer==null)throw new IllegalStateException("Default SkiaLayer constructor unavailable");
    JFrame frame=new JFrame("590 native attachment probe "+System.getProperty("naviamp.probe.label"));frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
    frame.addWindowListener(new WindowAdapter(){public void windowClosed(WindowEvent e){try{if(handle!=0)close.invoke(bridge,handle);layer.dispose();}catch(Exception failure){failure.printStackTrace(log);}log.close();System.exit(0);}});
    layer.setRenderDelegate(new SkikoRenderDelegate(){int frames;
     public void onRender(org.jetbrains.skia.Canvas canvas,int w,int h,long time){
      event("record frame="+(++frames)+" size="+w+"x"+h+" showing="+layer.isShowing());
      canvas.clear(0xff1640a0);
      if(handle==0 && !Boolean.getBoolean("naviamp.probe.skipNative")) {
       if(Boolean.getBoolean("naviamp.probe.defer"))EventQueue.invokeLater(NativeRasterAttachmentProbe::attach);else attach();
      }
     }});
    layer.addKeyListener(new KeyAdapter(){public void keyPressed(KeyEvent e){event("key="+e.getKeyCode());if(e.getKeyCode()==KeyEvent.VK_A)attach();else layer.needRedraw();}});
    layer.addComponentListener(new ComponentAdapter(){public void componentResized(ComponentEvent e){event("resize");}public void componentShown(ComponentEvent e){event("shown");}});
    frame.add(layer);frame.setSize(936,633);frame.setLocation(30,30);frame.setVisible(true);event("visible returned");
   }catch(Throwable e){e.printStackTrace(log);log.flush();}
  });
 }
}
