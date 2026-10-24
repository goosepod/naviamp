import java.awt.image.*;import javax.imageio.*;import java.nio.file.*;import java.util.*;
class MobilePixelDiff {
 public static void main(String[] args)throws Exception{
  Path p=Path.of(args[0]);
  for(String n:List.of("inline-sphere","inline-analog","inline-ocean","direct-sphere","direct45-sphere","direct-analog","direct45-analog","direct-ocean","direct45-ocean","direct-large-analog","direct-large-ocean","paused-direct-analog")){
   String[] s=Files.readString(p.resolve(n+"-before-bounds.txt")).strip().split(","); int x=(int)Float.parseFloat(s[0]),y=(int)Float.parseFloat(s[1]),w=(int)Float.parseFloat(s[2]),h=(int)Float.parseFloat(s[3]);
   BufferedImage a=ImageIO.read(p.resolve(n+"-before.png").toFile()),b=ImageIO.read(p.resolve(n+"-after.png").toFile());Set<Integer> colors=new HashSet<>();int changed=0,sibling=0;
   for(int j=y;j<y+h;j++)for(int i=x;i<x+w;i++){int ca=a.getRGB(i,j),cb=b.getRGB(i,j); colors.add(ca);if(diff(ca,cb))changed++;}
   for(int j=y+h+100;j<a.getHeight()-150;j++)for(int i=x;i<a.getWidth()-60;i++)if(diff(a.getRGB(i,j),b.getRGB(i,j)))sibling++;
   System.out.println(n+" colors="+colors.size()+" changed_gt2="+changed+" pixels="+(w*h)+" sibling_changed_gt2="+sibling);
  }
 }
 static boolean diff(int a,int b){for(int k=0;k<24;k+=8)if(Math.abs(((a>>k)&255)-((b>>k)&255))>2)return true;return false;}
}
