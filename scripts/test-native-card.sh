#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
D=$(mktemp -d); trap 'rm -rf "$D"' EXIT
P=extensions/automotive/src/main/java/app/morphe/extension/mapsautomotive/patches
mkdir -p "$D/src/android/graphics"
cat > "$D/src/android/graphics/Canvas.java" <<'JAVA'
package android.graphics;
public class Canvas { public String text; public void drawText(String t,float x,float y,Paint p){text=t;} }
JAVA
cat > "$D/src/android/graphics/Paint.java" <<'JAVA'
package android.graphics;
public class Paint { public static final int ANTI_ALIAS_FLAG=1; public enum Align { CENTER } private float s; public Paint(int f){} public void setTextAlign(Align a){} public void setColor(int c){} public void setTextSize(float v){s=v;} public float getTextSize(){return s;} public float measureText(String t){return t.length()*s/2;} }
JAVA
cat > "$D/src/ColorScheme.java" <<'JAVA'
package app.morphe.extension.mapsautomotive.patches;
public final class ColorScheme { public static boolean night; public static boolean nativeCardNight(){return night;} public static String nativeCardLabel(){return "Arrival battery";} }
JAVA
cat > "$D/src/ReportLog.java" <<'JAVA'
package app.morphe.extension.mapsautomotive.patches;
public final class ReportLog { static int errors; static void w(String t,String m,Throwable e){errors++;} }
JAVA
cat > "$D/src/aofi.java" <<'JAVA'
public interface aofi { Object a(Object x); }
JAVA
cat > "$D/src/MockNative.java" <<'JAVA'
import java.util.*;
class bmh { float w,h,left,top,right,bottom; aofi draw; }
class anv { static bmh f(bmh m,float w,float h,int mask){m.w=w;m.h=h;return m;} }
class qp { static bmh f(bmh m,float l,float t,float r,float b){m.left=l;m.top=t;m.right=r;m.bottom=b;return m;} }
class qn { static bmh c(bmh m,aofi draw){m.draw=draw;return m;} }
class qus { public final int f; qus(int x){f=x;} }
class qut { public final qus b; final boolean invalid; qut(int x,boolean invalid){b=new qus(x);this.invalid=invalid;} public boolean b(){return invalid;} }
class sp {}
class bsa extends sp { static final bsa a=new bsa(); }
class aobo { static final aobo a=new aobo(); }
class ckp { public final int weight; ckp(int w){weight=w;} }
interface bpu {}
class bpf implements bpu { final android.graphics.Canvas a=new android.graphics.Canvas(); }
class brv { final bpf wrapper=new bpf(); bpu b(){return wrapper;} }
class caq {
 final brv context=new brv(); public brv o(){return context;}
 final float width,height,density; final List<float[]> rects=new ArrayList<>(); final List<Integer> colors=new ArrayList<>();
 caq(float w,float h,float d){width=w;height=h;density=d;}
 private static long pair(float x,float y){return ((long)Float.floatToRawIntBits(x)<<32)|(Float.floatToRawIntBits(y)&0xffffffffL);}
 private static float hi(long v){return Float.intBitsToFloat((int)(v>>>32));}
 private static float lo(long v){return Float.intBitsToFloat((int)v);}
 public long m(){return pair(width,height);} public float a(){return density;}
 public void y(long color,long xy,long wh,long radius,sp fill,float alpha){
  if(fill!=bsa.a || alpha!=1f)throw new AssertionError("bad draw style");
  rects.add(new float[]{hi(xy),lo(xy),hi(wh),lo(wh),hi(radius)}); colors.add((int)(color>>>32));
 }
}
JAVA
cat > "$D/src/TestNativeCard.java" <<'JAVA'
import app.morphe.extension.mapsautomotive.patches.*;
class TestNativeCard {
 static int checks;
 static void yes(boolean x,String why){checks++;if(!x)throw new AssertionError(why);}
 static bmh decorate(int soc,boolean invalid,boolean night){ColorScheme.night=night;return (bmh)NatNativeCard.decorate(new bmh(),new qut(soc,invalid));}
 public static void main(String[] args){
  bmh card=decorate(53,false,true);
  yes(card.w==220&&card.h==124,"min size");
  yes(card.left==12&&card.top==24&&card.right==12&&card.bottom==22,"reserved padding");
  yes(card.draw!=null,"native draw installed");
  caq scope=new caq(440,248,2);
  yes(card.draw.a(scope)==aobo.a,"returns native unit");
  yes(scope.rects.size()==3,"background + track + fill");
  yes(scope.colors.get(0)==0xff234b70,"night background");
  yes("Arrival battery".equals(scope.context.wrapper.a.text),"native canvas title");
  yes(((ckp)NatNativeCard.boldWeight()).weight==700,"bold native font weight");
  yes(Math.abs(scope.rects.get(2)[2]-392*.53f)<.01f,"native SOC fill");
  yes(scope.rects.get(1)[1]==210,"bar in reserved area");
  bmh day=decorate(53,false,false); caq daytime=new caq(220,124,1);day.draw.a(daytime);
  yes(daytime.colors.get(0)==0xffe4effb,"day background");
  yes(!card.draw.equals(day.draw),"mode change invalidates draw modifier");
  yes(card.draw.equals(decorate(53,false,true).draw),"same SOC and theme stable equality");
  for(int soc:new int[]{-49,0,-99}){caq c=new caq(220,124,1);decorate(soc,false,true).draw.a(c);yes(c.rects.size()==2,"zero fill for SOC "+soc);}
  caq missing=new caq(220,124,1);decorate(53,true,true).draw.a(missing);yes(missing.rects.size()==2,"invalid data no invented bar");
  caq max=new caq(220,124,1);decorate(100,false,true).draw.a(max);yes(max.rects.get(2)[2]==196,"full bar");
  caq tiny=new caq(20,20,1);decorate(53,false,true).draw.a(tiny);yes(tiny.rects.size()==1,"small native constraints no overlap");
  caq bad=new caq(Float.NaN,124,1);card.draw.a(bad);yes(bad.rects.isEmpty(),"invalid size ignored");
  Object incompatible=new Object();yes(NatNativeCard.decorate(incompatible,new qut(53,false))==incompatible,"safe original fallback");
  System.out.println("PASS: "+checks+" native card mock checks (not Android runtime)");
 }
}
JAVA
javac -d "$D/classes" "$P/NatNativeCard.java" "$D/src/"*.java "$D/src/android/graphics/"*.java
java -cp "$D/classes" TestNativeCard
